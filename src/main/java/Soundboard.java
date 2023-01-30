import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseMotionListener;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static java.awt.Toolkit.getDefaultToolkit;

public class Soundboard extends JFrame {

    File soundFolder;
    List<Sound> soundFiles;
    JTabbedPane centerPanel;
    JPanel bottomPanel;
    JButton showOpenFolderButton, playButton, stopButton, pauseButton, testButton;
    JList<Sound> soundList;
    JList<Mixer.Info> audioDeviceList;
    JFileChooser fileChooser;
    JScrollPane listScroll;

    Overlay overlay;
    AudioPlayer player = new AudioPlayer();
    Config config = new Config();

    final Toolkit toolkit = getDefaultToolkit();
    final Dimension screenSize = toolkit.getScreenSize();

    boolean togglePlay = false;
    boolean hideOverlay = false;
    volatile boolean selectingKey = false;

    int lastKeyCode = -1;

    public Soundboard(){
        super();
        setMinimumSize(new Dimension(screenSize.width / 3, screenSize.height / 3));
        //setLocationByPlatform(true);
        setLocation((screenSize.width - getWidth()) / 2, (screenSize.height - getHeight()) / 2);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setTitle("On screen soundboard.");
        setLayout(new BorderLayout());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                config.save();
            }
        });

        fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setCurrentDirectory(config.getLastFolder());
        fileChooser.addActionListener(e -> config.setLastFolder(fileChooser.getSelectedFile()));

        centerPanel = new JTabbedPane();
        add(centerPanel, BorderLayout.CENTER);

        initBottomPanel();
        initSoundsTab();
        initAudioDevicesTab();
        initOptionsTab();

        openSelectedFolder(); // always open music folder on start.
        initOverlay();
        player.setMasterGain(0f);

        try {
            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(new KeyListener());
            GlobalScreen.addNativeMouseMotionListener(new MouseMotionListener());
        }catch (NativeHookException exception){
            System.err.println("Native hook was not registered.");
            exception.printStackTrace();
            System.exit(1);
        }

        setVisible(true);
    }

    class KeyListener implements NativeKeyListener {
        private final HashMap<Integer, Boolean> repeatedEvents = new HashMap<>();

        @Override
        public void nativeKeyPressed(NativeKeyEvent nativeEvent) {
            if (nativeEvent.getKeyLocation() != NativeKeyEvent.KEY_LOCATION_STANDARD)
                return;
            int kc = nativeEvent.getKeyCode();

            if(selectingKey) {
                lastKeyCode = nativeEvent.getKeyCode();
                selectingKey = false;
                return;
            }

            if(kc == config.previousKey.getKeyCode())
                prevSound();
            else if (kc == config.nextKey.getKeyCode())
                nextSound();
            else if (kc == config.togglePlayKey.getKeyCode()) {
                togglePlay(repeatedEvents.get(kc));
            } else if (kc == config.cueKey.getKeyCode())
                playSound();
            else if(kc == config.pauseKey.getKeyCode())
                player.togglePause();
            else if (kc == config.stopAllKey.getKeyCode())
                stop();

            System.out.println(kc + " " + NativeKeyEvent.getKeyText(kc));

            repeatedEvents.put(kc, true);
        }

        @Override
        public void nativeKeyReleased(NativeKeyEvent nativeEvent) {
            repeatedEvents.put(nativeEvent.getKeyCode(), false);
        }
    }

    class MouseMotionListener implements NativeMouseMotionListener{
        boolean shouldHideLast = true;

        @Override
        public void nativeMouseMoved(NativeMouseEvent nativeEvent) {
            if(overlay == null || hideOverlay)
                return;
            Point point = nativeEvent.getPoint();
            boolean shouldHide = point.x > overlay.getX() && point.x < overlay.getX() + overlay.getWidth() &&
                    point.y > overlay.getY() && point.y < overlay.getY() + overlay.getHeight();
            if (shouldHide != shouldHideLast)
                overlay.setVisible(!shouldHide);

            shouldHideLast = shouldHide;
        }
    }

    private void initOverlay(){
        overlay = new Overlay();
        overlay.setData(soundList.getModel());
        overlay.setVisible(true);
    }

    private void initBottomPanel(){
        bottomPanel = new JPanel();
        bottomPanel.setLayout(new FlowLayout());
        add(bottomPanel, BorderLayout.SOUTH);

        showOpenFolderButton = new JButton("Open folder");
        showOpenFolderButton.addActionListener(e -> {
            int ret = fileChooser.showOpenDialog(this);
            if (ret == JFileChooser.APPROVE_OPTION)
                openSelectedFolder();
        });
        bottomPanel.add(showOpenFolderButton);

        playButton = new JButton("Play sound");
        playButton.addActionListener(e -> playSound());
        bottomPanel.add(playButton);

        stopButton = new JButton("Stop playing");
        stopButton.addActionListener(e -> stop());
        bottomPanel.add(stopButton);

        pauseButton = new JButton("Pause");
        pauseButton.addActionListener(e -> player.togglePause());
        bottomPanel.add(pauseButton);

        /*testButton = new JButton("test");
        testButton.addActionListener(e -> {
            if(overlay == null){
                overlay = new Overlay();
                System.out.println("created " + overlay);
            }
            else {
                System.out.println("destroyed");
                overlay.dispose();
                overlay = null;
            }
        });
        bottomPanel.add(testButton);*/
    }

    private void initSoundsTab(){
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JTextField searchField = new JTextField();
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter((DefaultListModel<Sound>) soundList.getModel(), searchField.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter((DefaultListModel<Sound>) soundList.getModel(), searchField.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter((DefaultListModel<Sound>) soundList.getModel(), searchField.getText());
            }

            private void filter(DefaultListModel<Sound> model, String filter){
                boolean addAll = filter.isEmpty();
                model.clear();
                for(Sound sound : soundFiles){
                    if(sound.toString().contains(filter) || addAll)
                        model.addElement(sound);
                }

                if (model.isEmpty())
                    searchField.setForeground(Color.red);
                else
                    searchField.setForeground(Color.black);
            }
        });
        panel.add(searchField, BorderLayout.NORTH);

        soundList = new JList<>();
        soundList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        soundList.setLayoutOrientation(JList.VERTICAL);
        soundList.setVisibleRowCount(-1);
        soundList.addListSelectionListener(e -> {
            if (overlay != null)
                overlay.setSelectionIndex(soundList.getSelectedIndex());
        });

        listScroll = new JScrollPane(soundList);
        panel.add(listScroll, BorderLayout.CENTER);

        centerPanel.addTab("Sound list", panel);
    }

    private void initAudioDevicesTab(){
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JPanel buttons = new JPanel();

        JButton researchButton = new JButton("Re-search audio devices");
        researchButton.addActionListener(e -> {
            List<String> lastAudioDevices = new ArrayList<>();
            List<Mixer.Info> mis = audioDeviceList.getSelectedValuesList();

            for (Mixer.Info mi : mis) {
                lastAudioDevices.add(mi.getName());
            }

            searchForAudioDevices(lastAudioDevices);
        });
        buttons.add(researchButton);

        audioDeviceList = new JList<>();
        audioDeviceList.setCellRenderer(new CheckboxListCellRenderer());
        audioDeviceList.setSelectionModel(new DefaultListSelectionModel(){
            @Override
            public void setSelectionInterval(int index0, int index1) {
                if(super.isSelectedIndex(index0)) {
                    super.removeSelectionInterval(index0, index1);
                }
                else {
                    super.addSelectionInterval(index0, index1);
                }
            }
        });
        audioDeviceList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        audioDeviceList.setLayoutOrientation(JList.VERTICAL);
        audioDeviceList.setVisibleRowCount(-1);

        audioDeviceList.addListSelectionListener(e -> {
            ArrayList<String> deviceList = new ArrayList<>();
            for(Mixer.Info info : audioDeviceList.getSelectedValuesList()){
                deviceList.add(info.getName());
            }

            config.setSelectedAudioDevices(deviceList);
        });

        searchForAudioDevices(config.getSelectedAudioDevices());

        JScrollPane listScroll = new JScrollPane(audioDeviceList);
        listScroll.setMinimumSize(new Dimension(screenSize.width / 5 / 3, screenSize.height / 5 / 3));
        panel.add(listScroll, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.EAST);

        centerPanel.addTab("Audio devices", panel);
    }

    public void searchForAudioDevices(List<String> lastAudioDevices){
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
        List<Mixer.Info> lastSelectedMixers = new ArrayList<>();
        DefaultListModel<Mixer.Info> audioDeviceListModel = new DefaultListModel<>();
        for (Mixer.Info mi : mixerInfos){
            Mixer m = AudioSystem.getMixer(mi);
            Line.Info[] slis = m.getSourceLineInfo();
            for (Line.Info li : slis){
                try {
                    Line line = AudioSystem.getLine(li);
                    if (line instanceof SourceDataLine){
                        audioDeviceListModel.addElement(mi);
                        if (lastAudioDevices.contains(mi.getName()))
                            lastSelectedMixers.add(mi);
                    }
                } catch (LineUnavailableException e) {
                    e.printStackTrace();
                }
            }
        }

        audioDeviceList.setModel(audioDeviceListModel);

        for(Mixer.Info mi : lastSelectedMixers) {
            audioDeviceList.setSelectedIndex(audioDeviceListModel.indexOf(mi));
        }
    }

    private void initOptionsTab(){
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));

        JCheckBox hideOverlayBox = new JCheckBox("Hide overlay");
        hideOverlayBox.addActionListener(e -> {
            hideOverlay = hideOverlayBox.isSelected();
            overlay.setVisible(!hideOverlay);
        });
        buttonPanel.add(hideOverlayBox);

        JButton reloadOverlay = new JButton("Reload overlay");
        reloadOverlay.addActionListener(e -> {
            overlay.dispose();
            initOverlay();
            overlay.setVisible(!hideOverlayBox.isSelected());
        });
        buttonPanel.add(reloadOverlay);

        buttonPanel.add(new JLabel("Master gain control", JLabel.CENTER));

        JSlider volumeControl = new JSlider(JSlider.HORIZONTAL, -8000, 600, 0);
        volumeControl.setMajorTickSpacing(1000);
        volumeControl.setMinorTickSpacing(100);
        volumeControl.setPaintTicks(true);
        volumeControl.addChangeListener(e -> player.setMasterGain(((float)volumeControl.getValue()) / 100));
        buttonPanel.add(volumeControl);

        JPanel keyOptions = new JPanel();
        //SpringLayout layout = new SpringLayout();
        GridLayout layout = new GridLayout(0, 2, 6, 6);
        keyOptions.setLayout(layout);

        Config.KeyBinding[] keyBindings = {config.togglePlayKey, config.cueKey, config.pauseKey, config.stopAllKey, config.previousKey, config.nextKey};

        for (Config.KeyBinding keyBinding : keyBindings) {
            final Config.KeyBinding key = keyBinding;
            final JLabel label = new JLabel(key.description, SwingConstants.CENTER);
            final JButton changeKey = new JButton(key.getName());
            final Timer timer = new Timer(0, e1 -> {
                if (!selectingKey) {
                    ((Timer) e1.getSource()).stop();
                    key.setKeyCode(lastKeyCode);
                    changeKey.setText(key.getName());
                    changeKey.setToolTipText("");
                    lastKeyCode = -1;
                }
            });
            changeKey.addActionListener(e -> {
                if(selectingKey) {
                    timer.stop();
                    changeKey.setText(key.getName());
                    selectingKey = false;
                    lastKeyCode = -1;
                    return;
                }
                selectingKey = true;
                changeKey.setText("> press key <");
                changeKey.setToolTipText("Press again to stop.");
                keyOptions.repaint();
                timer.start();
            });

            label.setLabelFor(changeKey);
            keyOptions.add(label);
            keyOptions.add(changeKey);
        }

        //SpringUtilities.makeCompactGrid(keyOptions, keyBindings.length, 2, 6, 6, 6, 6);
        panel.add(keyOptions);
        panel.add(buttonPanel);

        centerPanel.addTab("Options", panel);
    }

    public void nextSound(){
        if (this.isActive())
            return;
        if(soundList.getSelectedIndex() == soundList.getModel().getSize() - 1)
            soundList.setSelectedIndex(0);
        else
            soundList.setSelectedIndex(soundList.getSelectedIndex() + 1);
        if(!isAppVisible())
            overlay.toFront();
    }

    public void prevSound(){
        if (this.isActive())
            return;
        if (soundList.getSelectedIndex() == 0)
            soundList.setSelectedIndex(soundList.getModel().getSize() - 1);
        else
            soundList.setSelectedIndex(soundList.getSelectedIndex() - 1);
        if(!isAppVisible())
            overlay.toFront();
    }

    public void togglePlay(boolean repeated){
        if(repeated)
            return;
        togglePlay();
        if(!isAppVisible())
            overlay.toFront();
    }

    public void stop(){
        if (player.isPlaying())
            player.stop();
    }

    public boolean isAppVisible(){
        return overlay.isActive() || this.isActive();
    }

    public void togglePlay(){
        togglePlay = !togglePlay;
        if (togglePlay)
            playSound();
        else if(player.isPlaying())
            player.stop();
    }

    public void openSelectedFolder(){
        soundFolder = fileChooser.getSelectedFile();
        if (soundFolder == null)
            soundFolder = config.getLastFolder();
        DefaultListModel<Sound> listModel = new DefaultListModel<>();
        File[] files = soundFolder.listFiles((dir, name) -> name.endsWith(".mp3"));

        if (files == null)
            return;

        soundFiles = new ArrayList<>();
        for (File file : files) {
            Sound sound = new Sound(file);
            listModel.addElement(sound);
            soundFiles.add(sound);
        }

        soundList.setModel(listModel);
        if(overlay != null)
            overlay.setData(listModel);
    }

    public void playSound(){
        Sound sound = soundList.getSelectedValue();
        if(sound == null) {
            JOptionPane.showMessageDialog(this, "No sound selected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (audioDeviceList.getSelectedValuesList().size() > 0) {
            player.play(sound.soundFile, audioDeviceList.getSelectedValuesList());
        }else {
            player.play(sound.soundFile);
        }
    }

    public static void main(String[] args) {
        new Soundboard();
    }
}
