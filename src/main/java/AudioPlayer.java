import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.*;
import javax.sound.sampled.DataLine.Info;

import static javax.sound.sampled.AudioSystem.getAudioInputStream;
import static javax.sound.sampled.AudioFormat.Encoding.PCM_SIGNED;


public class AudioPlayer {
    private static final byte[] EMPTY_BUFFER = new byte[4096];

    private AudioInputStream in;
    private AudioFormat outFormat;
    private final List<PlayerThread> playerThreads = new ArrayList<>();

    private boolean stopped = false;
    private boolean playing = false;
    private boolean pause = false;

    private float masterGain = 1.0f;

    private class PlayerThread implements Runnable{
        private final SourceDataLine line;
        private final AudioInputStream in;

        public PlayerThread(SourceDataLine line, AudioInputStream in){
            this.line = line;
            this.in = in;
        }

        @Override
        public void run() {
            try {
                playing = true;
                line.open(outFormat);
                setMasterGain(masterGain);
                line.start();
                stream(getAudioInputStream(outFormat, in), line);
                line.drain();
                line.stop();
                playing = false;
            } catch (LineUnavailableException | IOException e) {
                e.printStackTrace();
            }
            reset();
            playerThreads.remove(this);
        }

        public void setMasterGain(float volume){
            if (line.isOpen()) {
                FloatControl floatControl = (FloatControl) line.getControl(
                        FloatControl.Type.MASTER_GAIN);
                floatControl.setValue(volume);
            }
        }
    }

    /**
     * Play audio file on default audio device.
     * @param file audio file
     */
    public void play(File file) {
        try {
            in = getAudioInputStream(file);
            outFormat = getOutFormat(in.getFormat());
            final Info info = new Info(SourceDataLine.class, outFormat);

            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            startPlayerThread(line, file);
        } catch (UnsupportedAudioFileException | LineUnavailableException | IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Play audio file on specified audio device.
     * @param file audio file
     * @param info mixer info of that device
     */
    public void play(File file, Mixer.Info info){
        SourceDataLine line;
        try {
            in = getAudioInputStream(file);
            outFormat = getOutFormat(in.getFormat());

            line = AudioSystem.getSourceDataLine(outFormat, info);
            startPlayerThread(line, file);
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            throw new IllegalStateException(e);
        }

    }

    /**
     * Play audio file on specified devices.
     * @param file audio file
     * @param infos list of mixer infos pointing to that device.
     */
    public void play(File file, List<Mixer.Info> infos){
        List<SourceDataLine> lines;
        try {
            in = getAudioInputStream(file);
            outFormat = getOutFormat(in.getFormat());

            lines = new ArrayList<>();
            for (Mixer.Info mixerInfo : infos){
                SourceDataLine line = AudioSystem.getSourceDataLine(outFormat, mixerInfo);
                lines.add(line);
            }

            for (SourceDataLine line : lines){
                startPlayerThread(line, file);
            }

        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            throw new IllegalStateException(e);
        }
    }

    public void togglePause(){
        if (!playing)
            return;
        pause = !pause;
    }

    public void stop(){
        if (playing)
            stopped = true;
    }

    public boolean isPlaying(){
        return playing;
    }

    public void setMasterGain(float masterGain){
        this.masterGain = masterGain;
        for (PlayerThread playerThread : playerThreads){
            playerThread.setMasterGain(masterGain);
        }
    }

    private void startPlayerThread(SourceDataLine line, File file) throws UnsupportedAudioFileException, IOException {
        if(line == null)
            return;
        PlayerThread pt = new PlayerThread(line, getAudioInputStream(file));
        playerThreads.add(pt);
        Thread soundThread = new Thread(pt);
        soundThread.start();
    }

    private AudioFormat getOutFormat(AudioFormat inFormat) {
        final int ch = inFormat.getChannels();
        final float rate = inFormat.getSampleRate();
        return new AudioFormat(PCM_SIGNED, rate, 16, ch, ch * 2, rate, false);
    }

    private void stream(AudioInputStream in, SourceDataLine line)
            throws IOException {
        final byte[] buffer = new byte[4096];
        for (int n = 0; n != -1; n = in.read(buffer, 0, buffer.length)) {
            if (stopped)
                break;
            while (pause){
                line.write(EMPTY_BUFFER, 0, 4096);
            }
            line.write(buffer, 0, n);
        }
    }

    private void reset(){
        stopped = false;
        pause = false;
    }
}
