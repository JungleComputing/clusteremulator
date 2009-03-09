package clusteremulator;

import java.util.Properties;

import ibis.util.TypedProperties;

public class ConfigProperties {

    private static ConfigProperties uniqueInstance = new ConfigProperties();

    private TypedProperties prop;

    protected ConfigProperties() {
        prop = new TypedProperties();
        prop.addProperties(System.getProperties());
    }

    public static ConfigProperties getInstance() {
        return uniqueInstance;
    }

    public Properties getProperties() {
        return prop;
    }

    public boolean getBooleanProperty(String name, boolean defaultValue) {
        return prop.getBooleanProperty(name, defaultValue);
    }

    public int getIntProperty(String name, int defaultValue) {
        return prop.getIntProperty(name, defaultValue);
    }

    public long getLongProperty(String name, long defaultValue) {
        return prop.getLongProperty(name, defaultValue);
    }

    public String getStringProperty(String name, String defaultValue) {
        return prop.getProperty(name, defaultValue);
    }

}
