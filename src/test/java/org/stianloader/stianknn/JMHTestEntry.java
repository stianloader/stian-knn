package org.stianloader.stianknn;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

public class JMHTestEntry {
    public static void main(String[] args) throws IOException {
        URLClassLoader classLoader = (URLClassLoader) JMHTestEntry.class.getClassLoader();
        StringBuilder classpath = new StringBuilder();
        for (URL url : classLoader.getURLs()) {
            classpath.append(url.getPath()).append(File.pathSeparator);
        }
        System.setProperty("java.class.path", classpath.toString());

        org.openjdk.jmh.Main.main(args);
    }
}
