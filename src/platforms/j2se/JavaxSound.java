import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;

/*
    This file is part of JavaGear.
    
    Copyright (c) 2002-2008 Chris White
    All rights reserved. 
    
    Redistribution and use of this code or any derivative works are permitted
    provided that the following conditions are met: 
    
    * Redistributions may not be sold, nor may they be used in a commercial
    product or activity. 
    
    * Redistributions that are modified from the original source must include the
    complete source code, including the source code for all components used by a
    binary built from the modified sources. However, as a special exception, the
    source code distributed need not include anything that is normally distributed
    (in either source or binary form) with the major components (compiler, kernel,
    and so on) of the operating system on which the executable runs, unless that
    component itself accompanies the executable.
    
    * Redistributions must reproduce the above copyright notice, this list of
    conditions and the following disclaimer in the documentation and/or other
    materials provided with the distribution. 
    
    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
    AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
    ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE 
    LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
    CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
    SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
    INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
    CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
    ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
    POSSIBILITY OF SUCH DAMAGE.
*/

/**
 * - fix audio
 * - add flag to use a dedicated thread for sound - shouldn't be needed
 *
 * Copyright (c) 2019 Federico Berti
 */
public class JavaxSound
{
    private static boolean USE_AUDIO_THREAD = false;

    /** J2SE Sound Output */
    private SourceDataLine line;
    private int bufferSize = 1000;
    private volatile boolean stopSound = false;
    private volatile boolean dataAvailable = false;
    private volatile byte[] soundBuffer = new byte[0];
    private Thread soundThread;
    
    public void audioStart()
    {
        try
        {
            if (line == null)
            {
                // AudioFormat(float sample_rate(hz), int sampleSizeInBits, int channels, boolean signed, boolean bigEndian)
                AudioFormat af = new AudioFormat(Engine.SAMPLE_RATE, 8, 1, true, false);
                line = (SourceDataLine)AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, af));
                line.open(af, bufferSize);
                line.start();
                if(USE_AUDIO_THREAD) {
                    soundThread = new Thread(getSoundRunnable());
                    soundThread.start();
                }
            }
        }
        catch (Exception e)
        {
            if (Platform.DEBUG)
                e.printStackTrace();
        }        
    }
    
    public void audioStop()
    {
        if (line != null)
        {
            if(USE_AUDIO_THREAD) {
                stopSound = true;
                try {
                    soundThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            line.stop();
            line.close();
            line = null;
        }
    }

    public void audioOutput(byte[] buffer){
        if(USE_AUDIO_THREAD){
            audioOutputThread(buffer);
            return;
        }
        writeBuffer(buffer);
    }

    private void writeBuffer(byte[] buffer){
        if (line != null) {
            int start = 0;
            int len = buffer.length;
            do {
                int count = line.write(buffer, start, len);
                start += count;
            } while (start < len);
        }
    }

    
    public void audioOutputThread(byte[] buffer)
    {
        if (line != null)
        {
            int len = buffer.length;
            if(soundBuffer.length != len){
                soundBuffer = new byte[len];
            }
            System.arraycopy(buffer, 0, soundBuffer, 0, len);
            dataAvailable = true;
        }
    }

    private Runnable getSoundRunnable(){
        return () -> {
            do {
                while (!dataAvailable);
                writeBuffer(soundBuffer);
            } while (!stopSound);
        };
    }
}