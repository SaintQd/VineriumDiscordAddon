package org.saintqd.vineriumdiscordaddon.utils;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.NamedRecord;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import github.scarsz.discordsrv.dependencies.commons.io.IOUtils;
import net.kyori.adventure.key.Key;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.saintqd.vineriumdiscordaddon.VineriumDiscordAddon;
import org.saintqd.vineriumlib.VineriumLib;
import org.saintqd.vineriumlib.utils.VinUtils;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

public class GeoIp {

    private DatabaseReader reader;
    private String format;
    private boolean cityEnabled;

    public String getLocation(String ip) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            String city = "";
            String country = "";
            String leastSpecificSubdivision = "";
            String mostSpecificSubdivision = "";
            if (this.cityEnabled) {
                CityResponse response = this.reader.city(address);
                city = getName(response.city());
                country = getName(response.country());
                leastSpecificSubdivision = getName(response.leastSpecificSubdivision());
                mostSpecificSubdivision = getName(response.mostSpecificSubdivision());
            } else {
                CountryResponse response = this.reader.country(address);
                country = getName(response.country());
            }

            return format.replace("{CITY}",city)
                    .replace("{COUNTRY}",country)
                    .replace("{LEAST_SPECIFIC_SUBDIVISION}",leastSpecificSubdivision)
                    .replace("{MOST_SPECIFIC_SUBDIVISION}",mostSpecificSubdivision);
        } catch (IOException | GeoIp2Exception | NullPointerException e) {
            String defaultValue = VineriumLib.inst().getLangManager()
                    .getLangLines().getOrDefault(Key.key(VineriumDiscordAddon.inst(),"geoip_default_value"),"geoip_default_value");
            VinUtils.sendDebugMessage(1,"Could not get location for ip "+ip+". Using default value.");
            return defaultValue;
        }
    }

    private static String getName(NamedRecord response) {
        String defaultValue = VineriumLib.inst().getLangManager()
                .getLangLines().getOrDefault(Key.key(VineriumDiscordAddon.inst(),"geoip_default_value"),"geoip_default_value");
        return response.names().getOrDefault(VineriumDiscordAddon.inst().getConfig().getString("GeoIP.Locale"), defaultValue);
    }

    public void fetch() {
        fetch(VineriumDiscordAddon.inst().getServer().getConsoleSender());
    }

    public void fetch(CommandSender sender) {
        VineriumDiscordAddon.inst().getLogger().info("Trying to fetch GeoIP database...");
        if (!(sender instanceof ConsoleCommandSender))
            sender.sendRichMessage("<green>Trying to fetch GeoIP database...");
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        reader = null;
        Path dataPath = VineriumDiscordAddon.inst().getDataPath();
        ConfigurationSection geoIpConfig = VineriumDiscordAddon.inst().getConfig().contains("GeoIP")
                ? VineriumDiscordAddon.inst().getConfig().getConfigurationSection("GeoIP")
                : VineriumDiscordAddon.inst().getConfig();
        format = geoIpConfig.getString("Format","{CITY}, {COUNTRY}");
        cityEnabled = format.contains("{CITY}");

        Bukkit.getScheduler().runTaskAsynchronously(VineriumDiscordAddon.inst(), () -> {
            try {
                Path path = dataPath.resolve(cityEnabled ? "city.mmdb" : "country.mmdb");
                if (!Files.exists(path) || (System.currentTimeMillis() - path.toFile().lastModified())
                        > geoIpConfig.getLong("UpdateInterval",1209600000L)) {
                    String uri = cityEnabled ? geoIpConfig.getString("DBCityDownload")
                            : geoIpConfig.getString("DBCountryDownload");
                    uri = uri.replace("{LICENSE_KEY}",geoIpConfig.getString("LicenseKey",""));

                    URI parsedUri = new URI(uri);
                    ByteArrayInputStream byteStream = new ByteArrayInputStream(IOUtils.toByteArray(parsedUri.toURL().openStream()));
                    try (GZIPInputStream gzip = new GZIPInputStream(byteStream);
                         TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzip)) {
                        TarArchiveEntry entry;
                        byte[] b = new byte[4096];
                        while ((entry = tarInputStream.getNextEntry()) != null) {
                            if (entry.getName().endsWith("mmdb")) {
                                Files.deleteIfExists(path);

                                try (FileOutputStream fos = new FileOutputStream(path.toFile())) {
                                    int r;
                                    while ((r = tarInputStream.read(b)) != -1) {
                                        fos.write(b, 0, r);
                                    }
                                }
                            }
                        }
                    }
                }

                reader = new DatabaseReader.Builder(path.toFile()).withCache(new CHMCache(4096 * 4)).build();
                Bukkit.getScheduler().runTask(VineriumDiscordAddon.inst(), () -> {
                    VineriumDiscordAddon.inst().getLogger().info("GeoIP database successfully fetched.");
                    if (!(sender instanceof ConsoleCommandSender))
                        sender.sendRichMessage("<green>GeoIP database successfully fetched.");
                });

            } catch (IOException | URISyntaxException e) {
                Bukkit.getScheduler().runTask(VineriumDiscordAddon.inst(), () -> {
                    VineriumDiscordAddon.inst().getLogger().info("Could not fetch GeoIP database! Check console for stack trace.");
                    if (!(sender instanceof ConsoleCommandSender))
                        sender.sendRichMessage("<red>Could not fetch GeoIP database! Check console for stack trace.");
                });

                e.printStackTrace();
            }
        });
    }
}
