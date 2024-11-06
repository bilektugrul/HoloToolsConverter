package io.github.bilektugrul.htc;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import me.despical.commons.configuration.ConfigUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.sql.*;
import java.util.Base64;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class HoloToolsConverter extends JavaPlugin {

    public String tableName;
    public MySQLDatabase database;
    public boolean convertRunning = false;
    public int converted;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.tableName = getConfig().getString("table-name", "chests");
        this.database = new MySQLDatabase(this);
        if (getServer().getMinecraftVersion().equalsIgnoreCase("1.20.4")) {
            convert1204();
        } else {
            getServer().getScheduler().runTaskAsynchronously(this, this::convert121);
        }

    }

    public void convert1204() {
        FileConfiguration file = me.despical.commons.configuration.ConfigUtils.getConfig(this, "converted");
        String queryStr = "SELECT * FROM " + tableName;
        Connection connection = database.getConnection();

        try {
            Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery(queryStr);

            while (result.next()) {
                UUID uuid = UUID.fromString(result.getString("uuid"));

                JsonObject mainJson = new Gson().fromJson(result.getString("data"), JsonObject.class);
                JsonObject wardrobe = mainJson.getAsJsonObject("holo_wardrobe");
                for (String key : wardrobe.keySet()) {

                    String slotString = wardrobe.get(key).toString().replaceFirst("\"", "");
                    slotString = replaceLast(slotString, "\"", "");

                    JsonReader reader = new JsonReader(new StringReader(slotString.replace("\\", "")));
                    reader.setLenient(true);

                    JsonElement element = JsonParser.parseReader(reader);

                    JsonPrimitive helmetEnc = element.getAsJsonObject().getAsJsonPrimitive("helmet");
                    JsonPrimitive chestplateEnc = element.getAsJsonObject().getAsJsonPrimitive("chestplate");
                    JsonPrimitive leggingsEnc = element.getAsJsonObject().getAsJsonPrimitive("leggings");
                    JsonPrimitive bootsEnc = element.getAsJsonObject().getAsJsonPrimitive("boots");

                    ItemStack helmet, chestplate, leggings, boots;
                    String helmetByte, chestplateByte, leggingsByte, bootsByte;
                    if (helmetEnc != null) {
                        helmet = deserializeItemStack(helmetEnc.getAsString());
                        helmetByte = serializeToBase64(helmet);
                        file.set(uuid + "." + key + ".helmet", helmetByte);
                    }

                    if (chestplateEnc != null) {
                        chestplate = deserializeItemStack(chestplateEnc.getAsString());
                        chestplateByte = serializeToBase64(chestplate);
                        file.set(uuid + "." + key + ".chestplate", chestplateByte);
                    }

                    if (leggingsEnc != null) {
                        leggings = deserializeItemStack(leggingsEnc.getAsString());
                        leggingsByte = serializeToBase64(leggings);
                        file.set(uuid + "." + key + ".leggings", leggingsByte);
                    }

                    if (bootsEnc != null) {
                        boots = deserializeItemStack(bootsEnc.getAsString());
                        bootsByte = serializeToBase64(boots);
                        file.set(uuid + "." + key + ".boots", bootsByte);
                    }

                }

            }

            ConfigUtils.saveConfig(this, file, "converted");

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void convert121() {
        FileConfiguration file = ConfigUtils.getConfig(this, "converted");
        Connection connection = database.getConnection();

        for (String uuid : file.getKeys(false)) {

            JsonObject fullJson = new JsonObject();
            JsonObject wardrobe = new JsonObject();

            for (String slot : file.getConfigurationSection(uuid).getKeys(false)) {
                JsonObject slotJson = new JsonObject();

                String helmetStr = file.getString(uuid + "." + slot + ".helmet");
                String chestplateStr = file.getString(uuid + "." + slot + ".chestplate");
                String leggingsStr = file.getString(uuid + "." + slot + ".leggings");
                String bootsStr = file.getString(uuid + "." + slot + ".boots");

                String helmet, chestplate, leggings, boots;
                if (helmetStr != null) {
                    helmet = serializeItemStack(deserializeFromBase64(helmetStr));
                    slotJson.add("helmet", new JsonPrimitive(helmet));
                }

                if (chestplateStr != null) {
                    chestplate = serializeItemStack(deserializeFromBase64(chestplateStr));
                    slotJson.add("chestplate", new JsonPrimitive(chestplate));
                }

                if (leggingsStr != null) {
                    leggings = serializeItemStack(deserializeFromBase64(leggingsStr));
                    slotJson.add("leggings", new JsonPrimitive(leggings));
                }

                if (bootsStr != null) {
                    boots = serializeItemStack(deserializeFromBase64(bootsStr));
                    slotJson.add("boots", new JsonPrimitive(boots));
                }

                wardrobe.add(slot, new JsonPrimitive(String.valueOf(slotJson)));
            }

            fullJson.add("holo_wardrobe", wardrobe);
            String statementStr = "UPDATE `" + tableName + "` SET `data` = ? WHERE `uuid` = ?";

            try (PreparedStatement preparedStatement = connection.prepareStatement(statementStr)) {
                preparedStatement.setString(1, new Gson().toJson(fullJson));
                preparedStatement.setString(2, uuid);

                preparedStatement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        System.out.println("1.21 done.");

    }

    public static String replaceLast(String text, String regex, String replacement) {
        return text.replaceFirst("(?s)(.*)" + regex, "$1" + replacement);
    }

    public String serializeToBase64(ItemStack item) {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes());
    }

    public ItemStack deserializeFromBase64(String encoded) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
    }

    public String serializeItemStack(ItemStack var1) {
        try {
            ByteArrayOutputStream var2 = new ByteArrayOutputStream();
            GZIPOutputStream var3 = new GZIPOutputStream(var2);
            BukkitObjectOutputStream var4 = new BukkitObjectOutputStream(var3);
            var4.writeObject(var1);
            var4.close();
            return Base64.getEncoder().encodeToString(var2.toByteArray());
        } catch (Exception var5) {
            throw new IllegalStateException("Error transforming ItemStack to base64.", var5);
        }
    }

    public ItemStack deserializeItemStack(String var1) {
        try {
            ByteArrayInputStream var2 = new ByteArrayInputStream(Base64.getDecoder().decode(var1));
            GZIPInputStream var3 = new GZIPInputStream(var2);
            BukkitObjectInputStream var4 = new BukkitObjectInputStream(var3);
            ItemStack var5 = (ItemStack)var4.readObject();
            var4.close();
            return var5;
        } catch (ClassNotFoundException | IOException var6) {
            throw new IllegalStateException("Error transforming base64 to ItemStack.", var6);
        }
    }

}