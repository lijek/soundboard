import java.io.File;

public class Sound {
    public final File soundFile;

    public Sound(File soundFile) {
        this.soundFile = soundFile;
    }

    @Override
    public String toString() {
        return soundFile.getName();
    }
}
