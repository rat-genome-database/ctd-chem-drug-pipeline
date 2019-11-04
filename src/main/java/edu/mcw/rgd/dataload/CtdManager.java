package edu.mcw.rgd.dataload;

import edu.mcw.rgd.process.Utils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

/**
 * @author mtutaj
 * @since 3/21/12
 */
public class CtdManager {

    private CtdImporter importer;

    Logger log = Logger.getLogger("status");

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        CtdManager manager = (CtdManager) (bf.getBean("manager"));

        try {
            // run CTD importer
            manager.getImporter().run();
        } catch( Exception e ) {
            Utils.printStackTrace(e, manager.log);
            throw e;
        }
    }

    public CtdImporter getImporter() {
        return importer;
    }

    public void setImporter(CtdImporter importer) {
        this.importer = importer;
    }
}
