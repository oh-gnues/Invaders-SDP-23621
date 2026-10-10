package engine;

import java.io.BufferedInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

public class SoundManager {

    /**
     * Joue un fichier audio au format WAV.
     * @param fileName Nom du fichier audio (ex: "hit.wav")
     */
    public static void playSound(String fileName) {
        new Thread(() -> {
            try {
                InputStream audioSrc = SoundManager.class.getClassLoader().getResourceAsStream(fileName);
                if (audioSrc == null) {
                    audioSrc = new java.io.FileInputStream("res/" + fileName);
                }
                InputStream bufferedInput = new BufferedInputStream(audioSrc);
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedInput);
                Clip clip = AudioSystem.getClip();
                clip.open(audioStream);
                clip.start();
            } catch (Exception e) {
                System.err.println("Erreur de lecture du son : " + fileName);
                e.printStackTrace();
            }
        }).start();
    }
}