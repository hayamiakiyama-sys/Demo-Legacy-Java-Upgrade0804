package org.springframework.samples.petclinic.service.billing;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Instantiates the configured {@link DiscountPlugin} reflectively.
 */
@Component
public class DiscountPluginLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DiscountPluginLoader.class);

    private static final String CONFIG_LOCATION = "/billing/billing.properties";

    private static final String PLUGIN_KEY = "billing.discount.plugin";

    public DiscountPlugin load() {
        String className = readPluginClassName();
        try {
            Class<?> pluginClass = Class.forName(className);
            Constructor<?> constructor = pluginClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object plugin = constructor.newInstance();
            if (!(plugin instanceof DiscountPlugin)) {
                throw new IllegalStateException(className + " does not implement DiscountPlugin");
            }
            LOG.info("discount plugin loaded: {}", className);
            return (DiscountPlugin) plugin;
        } catch (Exception ex) {
            throw new IllegalStateException("failed to load discount plugin: " + className, ex);
        }
    }

    private String readPluginClassName() {
        InputStream in = getClass().getResourceAsStream(CONFIG_LOCATION);
        if (in == null) {
            return WeekdayDiscountPlugin.class.getName();
        }
        Properties properties = new Properties();
        try {
            properties.load(in);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to read " + CONFIG_LOCATION, ex);
        } finally {
            try {
                in.close();
            } catch (Exception ignored) {
                LOG.debug("could not close billing properties", ignored);
            }
        }
        return properties.getProperty(PLUGIN_KEY, WeekdayDiscountPlugin.class.getName());
    }
}
