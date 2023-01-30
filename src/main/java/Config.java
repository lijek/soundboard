import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Config {

    /*
    XML file structure:
    <Config>
        <LastFolder>./path</LastFolder>
        <LastSelectedAudioDevices>
            <Device>Glosniki ...</Device>
            <Device>Line 1</Device>
        </AudioDevices>
        <Keys>
            ...nie
        </Keys>
    </Config>
     */

    final String filename = "soundboard.xml";
    File file;
    Document doc;

    public final KeyBinding togglePlayKey = new KeyBinding(57420, "Toggle play");
    public final KeyBinding cueKey = new KeyBinding(3655, "Cue");
    public final KeyBinding pauseKey = new KeyBinding(3665, "Pause");
    public final KeyBinding stopAllKey = new KeyBinding(3657, "Stop all");
    public final KeyBinding previousKey = new KeyBinding(57416, "Previous sound");
    public final KeyBinding nextKey = new KeyBinding(57424, "Next sound");

    //CONSTANTS
    private final String CONFIG = "Config";
    private final String LAST_FOLDER = "LastFolder";
    private final String AUDIO_DEVICES = "LastSelectedAudioDevices";
    private final String DEVICE = "Device";

    public Config() {
        try {
            Path path = Paths.get(System.getProperty("user.home"), filename);
            file = path.toFile();

            if (!file.exists()) {
                file.createNewFile();
                doc = createXMLDoc();
                save();
            } else {
                DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
                doc = builder.parse(file);
            }
        }catch (IOException | ParserConfigurationException | SAXException e){
            throw new IllegalStateException(e);
        }
    }

    private Document createXMLDoc() throws ParserConfigurationException {
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element eRoot = doc.createElement(CONFIG);
        doc.appendChild(eRoot);

        Element eLastFolder = doc.createElement(LAST_FOLDER);
        eRoot.appendChild(eLastFolder);

        Element eAudioDevices = doc.createElement(AUDIO_DEVICES);
        eRoot.appendChild(eAudioDevices);

        return doc;
    }

    public void save(){
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(file);
            transformer.transform(source, result);
        } catch (TransformerException e) {
            e.printStackTrace();
        }
    }

    public void setSelectedAudioDevices(List<String> devices){
        Node eAudioDevices = doc.getElementsByTagName(AUDIO_DEVICES).item(0);

        while(eAudioDevices.hasChildNodes())
            eAudioDevices.removeChild(eAudioDevices.getFirstChild());

        for (String device : devices){
            Element eDevice = doc.createElement(DEVICE);
            eDevice.setTextContent(device);
            eAudioDevices.appendChild(eDevice);
        }
    }

    public List<String> getSelectedAudioDevices(){
        Node eAudioDevices = doc.getElementsByTagName(AUDIO_DEVICES).item(0);
        NodeList deviceList = eAudioDevices.getChildNodes();
        List<String> list = new ArrayList<>();

        for (int i = 0; i < deviceList.getLength(); i++) {
            Node device = deviceList.item(i);
            if (device.getNodeName().equals(DEVICE))
                list.add(device.getTextContent());
        }

        return list;
    }

    public void setLastFolder(File folder){
        Node eLastFolder = doc.getElementsByTagName(LAST_FOLDER).item(0);
        eLastFolder.setTextContent(folder.getAbsolutePath());
    }

    public File getLastFolder(){
        Node eLastFolder = doc.getElementsByTagName(LAST_FOLDER).item(0);
        if(eLastFolder.getTextContent() == null)
            return new File(System.getProperty("user.home") + System.getProperty("file.separator")+ "Music");
        return new File(eLastFolder.getTextContent());
    }

    public static class KeyBinding{
        private int keyCode;
        public final String description;

        public KeyBinding(int keyCode, String description){
            this.keyCode = keyCode;
            this.description = description;
        }

        public int getKeyCode() {
            return keyCode;
        }

        public void setKeyCode(int keyCode) {
            this.keyCode = keyCode;
        }

        public String getName() {
            return NativeKeyEvent.getKeyText(keyCode);
        }
    }
}
