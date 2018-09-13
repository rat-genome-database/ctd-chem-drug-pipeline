package edu.mcw.rgd.dataload;

import edu.mcw.rgd.process.Utils;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

/**
 * @author mtutaj
 * @since 3/21/12
 */
public class CtdManager {

    private CtdImporter importer;
    private String version;

    public static void main(String[] args) throws Exception {

        long time0 = System.currentTimeMillis();

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        CtdManager manager = (CtdManager) (bf.getBean("manager"));

        System.out.println(manager.getVersion());

        // run CTD importer
        manager.getImporter().run();

        System.out.println("--CTD Chemical Drug Interactions pipeline DONE --");
        System.out.println("--elapsed time: "+Utils.formatElapsedTime(time0, System.currentTimeMillis()));
    }


    public CtdImporter getImporter() {
        return importer;
    }

    public void setImporter(CtdImporter importer) {
        this.importer = importer;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
