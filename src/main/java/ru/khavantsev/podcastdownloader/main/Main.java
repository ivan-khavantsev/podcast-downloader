package ru.khavantsev.podcastdownloader.main;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {
    /*
            YYYY - year
            MM - month
            DD - day
            TITLE - title
            FILE - file
            EXT - extension
            NUMBER - number of item
         */
    private static String rss = null;
    private static String downloadPath = null;
    private static String pathFormat = "YYYY/MM/YYYY-MM-DD_FILE";
    private static Date fromDate = null;
    private static List<String> extensions = null;
    private static File podcastFile = null;
    private static String podcastFileDir;
    private static String podcastDatePattern = "EEE, dd MMM yyyy HH:mm:ss z";
    private static DateFormat df = new SimpleDateFormat(podcastDatePattern, Locale.ENGLISH);
    private static String lineSeparator = System.getProperty("line.separator");
    private static String fileSeparator = System.getProperty("file.separator");
    private static File hashesFile = null;
    private static Boolean checkHashes = true;

    public static void process() throws Exception {
        System.out.println("Program start");


        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        Document document = docBuilder.parse(rss);
        document.normalizeDocument();

        NodeList list = document.getElementsByTagName("item");

        System.out.println("Total items: " + list.getLength());
        for (Integer i = list.getLength()-1; i >= 0 ; i--) {
            Node node = list.item(i);
            NodeList list2 = node.getChildNodes();
            String title = null;
            String enclosure = null;
            Date date = null;
            for (int j = 0; j < list2.getLength(); j++) {
                Node node2 = list2.item(j);

                if (node2.getNodeName().equals("title")) {
                    title = node2.getTextContent();
                } else if (node2.getNodeName().equals("pubDate")) {
                    date = df.parse(node2.getTextContent());
                } else if (node2.getNodeName().equals("enclosure")) {
                    enclosure = node2.getAttributes().getNamedItem("url").getTextContent();
                }
            }

            title = title.trim().replaceAll("[\\/:*?\"<>|]", "");

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.setTimeZone(TimeZone.getTimeZone("GMT"));

            if (fromDate != null && fromDate.after(calendar.getTime())) {
                continue;
            }

            String year = String.valueOf(calendar.get(Calendar.YEAR));
            String month = String.format("%02d", (calendar.get(Calendar.MONTH) + 1));
            String day = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH));

            if (enclosure != null) {
                URL website = new URL(enclosure);
                String file = website.getPath();
                int idx = file.lastIndexOf("/");
                file = idx >= 0 ? file.substring(idx + 1) : file;
                file = file.replaceAll("\\+", "%2b");
                file = URLDecoder.decode(file, "UTF-8");

                int extIdx = file.lastIndexOf(".");
                String ext = extIdx >= 0 ? file.substring(extIdx + 1) : "mp3";

                if (extensions != null && !extensions.isEmpty()) {
                    if (!extensions.contains(ext.toLowerCase())) {
                        continue;
                    }
                }

                String fileName = pathFormat.replace("YYYY", year);
                fileName = fileName.replace("MM", month);
                fileName = fileName.replace("DD", day);
                fileName = fileName.replace("TITLE", title);
                fileName = fileName.replace("FILE", file);
                fileName = fileName.replace("EXT", ext);
                fileName = fileName.replace("NUMBER", new Integer(i+1).toString());


                File newFile = new File(downloadPath + fileSeparator + fileName);
                if (newFile.exists()) {
                    System.out.println((i+1) + ". " + fileName + " exist.");
                    continue;
                }

                try {
                    File tempFile = new File(downloadPath + fileSeparator + fileName + ".partial");
                    tempFile.getParentFile().mkdirs();

                    ReadableByteChannel rbc = Channels.newChannel(website.openStream());
                    FileOutputStream fos = new FileOutputStream(tempFile);
                    System.out.print(i+1 + ". " + fileName + " downloading...");
                    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                    fos.close();
                    tempFile.renameTo(newFile);

                    if (checkHashes) {
                        try (FileWriter sw = new FileWriter(hashesFile, true)) {
                            String md5 = md5File(newFile.getPath());
                            sw.write(md5 + " *" + fileName + lineSeparator);
                        }
                    }

                    System.out.println("ok.");
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            podcastFile = new File(args[0]);
            podcastFileDir = podcastFile.getAbsoluteFile().getParentFile().getAbsolutePath();

            Properties prop = new Properties();

            InputStream input = null;
            try {
                input = new FileInputStream(podcastFile);
                prop.load(input);

                rss = new String(prop.getProperty("rss").getBytes("ISO-8859-1"), "UTF-8");
                System.out.println("RSS: " + rss);

                if (prop.getProperty("path") != null) {
                    downloadPath = new String(prop.getProperty("path").getBytes("ISO-8859-1"), "UTF-8");
                    if (!new File(downloadPath).isAbsolute()) {
                        downloadPath = new File(podcastFileDir + fileSeparator + downloadPath).getAbsolutePath();
                    }
                } else {
                    downloadPath = podcastFileDir;
                }


                System.out.println("Path: " + downloadPath);

                if (prop.getProperty("format") != null) {
                    pathFormat = new String(prop.getProperty("format").getBytes("ISO-8859-1"), "UTF-8");
                    System.out.println("Format: " + pathFormat);
                }

                if (prop.getProperty("fromdate") != null) {
                    String fromDatePattern = "yyyy-MM-dd";
                    DateFormat fromDateFormat = new SimpleDateFormat(fromDatePattern, Locale.ENGLISH);
                    fromDate = fromDateFormat.parse(prop.getProperty("fromdate"));
                    System.out.println("DateFrom: " + fromDate);
                }

                if (prop.getProperty("extensions") != null) {
                    extensions = Arrays.asList(prop.getProperty("extensions").toLowerCase().split(","));
                    System.out.println("Extensions: " + prop.getProperty("extensions"));
                }

                if (prop.getProperty("check_hashes") != null) {
                    checkHashes = Boolean.parseBoolean(prop.getProperty("check_hashes"));
                    System.out.println("Check hashes: " + checkHashes);
                }

                if (prop.getProperty("check_hashes_file") != null) {
                    String hashesFilename = new String(prop.getProperty("check_hashes_file").getBytes("ISO-8859-1"), "UTF-8");
                    File tmp = new File(hashesFilename);
                    if (!tmp.isAbsolute()) {
                        hashesFile = new File(podcastFileDir + fileSeparator + hashesFilename);
                    } else {
                        hashesFile = tmp;
                    }
                    System.out.println("Hashes file: " + hashesFile.getAbsolutePath());
                } else {
                    hashesFile = new File(downloadPath + fileSeparator + "check.md5");
                }

                process();

            } catch (IOException ex) {
                ex.printStackTrace();
            } finally {
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        } else {
            System.out.println("Incorrect podcast information");
        }
    }

    public static String md5File(String filename) throws IOException {
        FileInputStream fis = new FileInputStream(new File(filename));
        String md5 = org.apache.commons.codec.digest.DigestUtils.md5Hex(fis).toLowerCase();
        fis.close();
        return md5;
    }
}
