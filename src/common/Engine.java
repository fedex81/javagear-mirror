import javax.swing.*;
import java.util.Random;
import java.io.*;

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

public class Engine extends Platform
    implements Runnable 
{       
    /** IDs for Platform version */
    public final static int
        J2SE = 0,   // Denote J2SE Version
        J2ME = 1;   // Denote J2ME Version
    
    // --------------------------------------------------------------------------------------------
    // References to classes
    // --------------------------------------------------------------------------------------------
    
    /** Reference to parent class */
    public static JavaGear parent;
    
    /** Reference to this class */
    public static Engine engine;
    
    // --------------------------------------------------------------------------------------------
    // Key Presses
    // --------------------------------------------------------------------------------------------
    
    /** Keys are mapped to the following in the device class */
    public final static int
        K_UP = 0x01,
        K_DOWN = 0x02,
        K_LEFT = 0x04,
        K_RIGHT = 0x08,
        K_FIRE1 = 0x10,
        K_FIRE2 = 0x20,
        K_START = 0x40,
        K_MENU = 0x80;
    
    /** Previous key pressed */
    private static int key;
    
    /** Platform specific keycode (for redefining keys) */
    public static int keyCode;
    
    // --------------------------------------------------------------------------------------------
    // Engine Internals
    // --------------------------------------------------------------------------------------------
        
    /** This thread */
    private Thread thread;
    
    /** Is thread running? */
    public static boolean running;
    
    /** Random number generator */
    private static Random rnd = new Random();
        
    /** JavaGear main state */
    public static int state;
    
    public final static int 
        S_EMULATE = 0,    // Emulate 
        S_MENU = 1,       // Display menu
        S_CLS = 2;        // Clear screen before resuming emulation
    
    /** Tick counter */
    private int tick;
    
    /** Base name of ROM image */
    private String romBaseName = Setup.PROGRAM_NAME;
    
    /** If this is a filename, javagear.conf is no longer used */
    public static String useRomFile = null;
    
    // --------------------------------------------------------------------------------------------
    // Emulation Related
    // --------------------------------------------------------------------------------------------
    
    /** NTSC Clock Speed (3579545Hz for NTSC systems) */
    private final static int CLOCK_NTSC = 3579545;
    
    /** PAL Clock Speed (3546893Hz for PAL/SECAM systems) */
    private final static int CLOCK_PAL = 3546893;
    
    /** CPU Cycles per scanline */
    public static int cyclesPerLine;
    
    /** Reference to Z80 CPU */
    public Z80 z80;
    
    /** Reference to video processor */
    public Vdp vdp;
    
    /** Reference to sound processor */
    public SN76489 psg;
    
    /** Reference to ports */
    public Ports ports;
    
    /** No of scanlines to render (including blanking) */
    public static int no_of_scanlines;

    /** Emulated screen pixels */
    public final static int display[] = new int[Vdp.SMS_WIDTH * Vdp.SMS_HEIGHT];
    
    /** Reference to display to render */
    public static int render[];
    
    /** Render every FRAMESKIP frames */
    public static int frameSkip = DEFAULT_FRAMESKIP;
    
    /** Throttle mode */
    public static boolean throttle = DEFAULT_THROTTLE;
    
    /** Force device to sleep for this many ms per frame */
    private int minSleep = DEFAULT_MIN_SLEEP;
    
    /** Target FPS (NTSC / PAL) */
    public int fps;
    
    /** Counter for frameskip */
    private int frameskip_counter;
    
    /** SMS Pause button pressed? */
    private static boolean pause_button;
    
    /** Length of time each rendered frame should take to execute */
    private int targetFrameTime;
    
    /** SMS Mode */
    public static boolean is_sms = true;
    
    /** GG Mode */
    public static boolean is_gg  = false; 

    // --------------------------------------------------------------------------------------------
    // Audio Related
    // --------------------------------------------------------------------------------------------
    
    /** Sound Enabled */
    public static boolean soundEnabled = SUPPORTS_SOUND;
    
    /** Audio buffer */
    private byte[] audioBuffer;
    
    /** Offset into audio buffer */
    private int audioBufferOffset;
    
    /** Number of samples to generate per frame */
    public static int samplesPerFrame;
    
    /** How many samples to generate per line */
    private int[] samplesPerLine;
    
    // --------------------------------------------------------------------------------------------
    // Platform Specific Functions
    // --------------------------------------------------------------------------------------------
    
    public final static int
        PLATFORM_THROTTLE_INIT = 0,
        PLATFORM_THROTTLE = 1,
        PLATFORM_FRAMESKIP = 2,
        PLATFORM_AUDIO_START = 3,
        PLATFORM_AUDIO_STOP = 4,
        PLATFORM_INIT_LIGHTGUN = 5,
        PLATFORM_STOP_LIGHTGUN = 6;
    
    // --------------------------------------------------------------------------------------------
    
    
    public Engine(JavaGear ta) 
    {
        super();
        
        parent = ta;
        engine = this;
             
        // Load font
//        font = divideImage(new PlatformImg(getFile("font.png")), FONT_SIZE, FONT_SIZE);
        
        state = S_MENU;
        uiState = UI_STARTUP;
    }

    private void init() 
    {           
        consoleClear();
        consolePrint("INITIALIZING...", true);        
        vdp = new Vdp(display);
        if (SUPPORTS_SOUND)
            psg = new SN76489();
        ports = new Ports(vdp, psg);
        z80 = new Z80(ports);       

        // Load Saved Settings
        loadSettings(); 
                
        consolePosition--;
        consolePrint("INITIALIZING...DONE", true);  
        consolePrint("LOAD ROM...", true);       
        
        if (useRomFile == null)
        {
            if (!readConfigFile("javagear.conf")) return;
        }
        else
        {
            if (!readRomDirectly(useRomFile))
            {
                setMessage("ERROR INITIALIZING ROM");
                return;
            }
        }
        
        consolePosition--;
        consolePrint("LOAD ROM...    DONE", true);   

        loadSRAM();        
        setupScreen(scaleEnabled);
        reset();
        
        consolePrintDivider();
        consolePrint("", false);
        consolePrint("PRESS FIRE TO START", false);
        consolePrint("PRESS MENU TO SETUP", true);

        uiState = UI_PRESSKEY;
    }
       
    /**
     * Reset all emulation
     */
    public void reset()
    {
        // Setup Default Timing
        setVideoTiming(Vdp.videoMode);
        
        frameCount = 0;
        frameskip_counter = frameSkip;
        
        z80.reset();
        z80.resetMemory(null);
        ports.reset();
        vdp.reset();
        
        for (int i = display.length; i-- != 0;)
            display[i] = 0;
        
        resetControllers();
    }
    
    // --------------------------------------------------------------------------------------------
    // File Loading Routines
    // --------------------------------------------------------------------------------------------
    
    public void initRom(String fileName)
    {
        readRomDirectly(fileName);
        reset();
        vdp.forceFullRedraw();       
        loadSRAM();      
        
        if (SUPPORTS_SOUND && soundEnabled) platformFunction(this, PLATFORM_AUDIO_START);
        if (ID == J2SE) platformFunction(this, PLATFORM_THROTTLE_INIT);
        state = S_EMULATE;
    }
    
    public byte[] getFile(String s)
    {
        try
        {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            InputStream is = getResourceAsStream(s);
            
            int read = 0;
            while ((read = is.read()) != -1)
            {
                bout.write(read);
            }
            is.close();
            byte[] bytes = bout.toByteArray();
            bout.close();
            return bytes;
        }
        catch (Exception e)
        {
            if (DEBUG)
            {
                System.out.println("getFile("+s+") - Failed to load resource");
                e.printStackTrace();
            }
            return null;
        }   
    }
    
    private boolean readConfigFile(String file)
    {
        try
        {
            DataInputStream in = new DataInputStream(getResourceAsStream(file));
            
            String romName = in.readUTF();
            String fileName = in.readUTF();
            int size = in.readInt();
            int mode = in.readByte();
            
            in.close();
                
            int seperator =  fileName.lastIndexOf('.');
            
            if (ID == J2SE)
                romBaseName = seperator != -1 ? fileName.substring(0, seperator) : fileName;
                        
            // Toggle SMS / GG emulation mode
            if (mode == 1) setSMS();
            else if (mode == 2) setGG();
            
            byte[][] pages = loadROM(getResourceAsStream(fileName), size);
            
            // Default Mapping (Needed or Shinobi doesn't work)
            z80.resetMemory(pages);
        }
        catch (Exception e)
        {
            setMessage(new String[] {"ERROR INITIALIZING ROM", "", "HAVE YOU RUN ROMTOOL?"});           
            return false;
        }
        return true;
    }
    
    /**
     * Bypass config file and directly load rom
     * 
     * @param fileName  Filename to load
     */
    private boolean readRomDirectly(String fileName)
    {
        byte[][] pages;
        int seperator =  fileName.lastIndexOf('.');
            
        romBaseName = seperator != -1 ? fileName.substring(0, seperator) : fileName;

        // J2SE: Support loading roms out of zip files
        if (ID == J2SE &&
            fileName.toLowerCase().endsWith(".zip"))
        {            
            pages = getZip(this, fileName);
            if (pages == null) return false;
        }
        else
        {
            int mode = (fileName.toLowerCase().endsWith(".gg")) ? 2 : 1;
                    
            // Toggle SMS / GG emulation mode
            if (mode == 1) setSMS();
            else if (mode == 2) setGG();
            
            int size = getResourceSize(fileName);
        
            if (size <= Setup.PAGE_SIZE)
                return false;
            
            pages = loadROM(getResourceOutsideJar(fileName), size);
         
            if (pages == null) return false;
        }
        // Default Mapping (Needed or Shinobi doesn't work)
        z80.resetMemory(pages);
        
        return true;
    }
    
    public byte[][] loadROM(InputStream is, int size)
    {       
        try
        {                
            // Strip 512 Byte File Headers
            if ((size % 1024) != 0)
            {
                is.skip(512); // skip 512 bytes
                size -= 512;
            }
            
            // Calculate number of pages from file size and create array appropriately
            int number_of_pages = (size / Setup.PAGE_SIZE);

            byte[][] pages = new byte[number_of_pages][Setup.PAGE_SIZE];
    
            // Read file into pages array
            for (int x=0; x < size; x += Setup.PAGE_SIZE)
                // second value is offset, third is length
                is.read(pages[x/Setup.PAGE_SIZE], 0x0000, Setup.PAGE_SIZE);
                
            is.close(); 

            return pages;
        }
        catch (IOException e)
        {
            if (DEBUG)
                e.printStackTrace();
            
            return null;
        }
    }
    
    // --------------------------------------------------------------------------------------------
    // State Saving Routines
    // --------------------------------------------------------------------------------------------
    
    private final static String STATE_VERSION = "1.0";
    private final static String EXT_STATE = ".jg";
    
    /** Current slot to save into */
    public int slot;
    
    /** Number of save slots */
    private final static int NUMBER_OF_SLOTS = 3;
    
    public boolean loadState()
    {
        try
        {
            byte[] data = load(romBaseName + EXT_STATE + slot);
            
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            DataInputStream inData = new DataInputStream(in);  
            
            inData.readUTF();
            inData.readUTF();
            boolean originalAccuracy = inData.readBoolean();

            // Restore Z80
            int[] z80State = new int[Z80.STATE_LENGTH];           
            for (int i = 0; i < z80State.length; i++)
                z80State[i] = inData.readInt();            
            z80.setState(z80State);
            z80State = null;
            
            // Restore RAM and SRAM
            for (int i = 0; i < z80.ram.length; i++)
                inData.read(z80.ram[i]);

            boolean useSRAM = inData.readBoolean();
            
            if (useSRAM)
            {
                for (int i = 0; i < SRAM_BANKS_TO_SAVE; i++)
                    inData.read(z80.sram[i]);
            }
            
            // Restore Memory Mapping
            int[] frameReg = new int[4];
            for (int i = 0; i < frameReg.length; i++)
                frameReg[i] = inData.readInt();
            z80.setStateMem(frameReg);
            frameReg = null;
            
            // Restore Ports
            int portLen = inData.readByte();
            if (BuildSettings.ACCURATE == originalAccuracy)
            {
                for (int i = 0; i < ports.ioPorts.length; i++)
                    ports.ioPorts[i] = inData.readInt();
            }
            // If not same version, then continue without restoring ports.
            // Could potentially cause problems with the restore.
            else
            {
                for (int i = 0; i < portLen; i++)
                    inData.readInt();
            }

            // Restore VDP State
            inData.read(vdp.VRAM);
            int vdpLength = inData.readInt();
            int[] vdpState = new int[vdpLength];
            for (int i = 0; i < vdpLength; i++)
                vdpState[i] = inData.readInt();
            vdp.setState(vdpState);
            vdpState = null;

            inData.close();
            in.close();  
                     
            // Restore other defaults           
            frameCount = 0;
            frameskip_counter = frameSkip;

            for (int i = display.length; i-- != 0;)
                display[i] = 0;
            
            resetControllers();
            
            setVideoTiming(Vdp.videoMode);
        }
        catch (Exception e)
        {
            return false;
        }
        
        return true;
    }
    
    public boolean saveState()
    {
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); 
            DataOutputStream outData = new DataOutputStream(out);  
            
            // Write header
            outData.writeUTF(Setup.PROGRAM_NAME);
            outData.writeUTF(STATE_VERSION);
            outData.writeBoolean(BuildSettings.ACCURATE);
            
            // Write Z80 state
            int[] z80State = z80.getState();
            for (int i = 0; i < z80State.length; i++)
                outData.writeInt(z80State[i]);
            z80State = null;
            
            // Write RAM and SRAM
            for (int i = 0; i < z80.ram.length; i++)
                outData.write(z80.ram[i]);
            
            outData.writeBoolean(z80.hasUsedSRAM());
            
            if (z80.hasUsedSRAM())
            {
                for (int i = 0; i < SRAM_BANKS_TO_SAVE; i++)
                    outData.write(z80.sram[i]);
            }
            
            // Write Memory Mapping state
            for (int i = 0; i < z80.frameReg.length; i++)
                outData.writeInt(z80.frameReg[i]);
            
            // Write ports
            outData.writeByte(ports.ioPorts.length);
            for (int i = 0; i < ports.ioPorts.length; i++)
                outData.writeInt(ports.ioPorts[i]);
                        
            // Write VDP state
            outData.write(vdp.VRAM);                     
            int[] vdpState = vdp.getState();
            outData.writeInt(vdpState.length);
            for (int i = 0; i < vdpState.length; i++)
                outData.writeInt(vdpState[i]);
            vdpState = null;
            
            byte[] data = out.toByteArray();                   
            
            outData.close();
            out.close();   
            
            save(romBaseName + EXT_STATE + slot, data);
        }
        catch (Exception e)
        {
            return false;
        }       
        return true;
    }
       
    // --------------------------------------------------------------------------------------------
    // Settings
    // --------------------------------------------------------------------------------------------
    
    public final static String FILE_SETTINGS = "javagear.settings";
    private final static String EXT_SRAM = ".sram";
    
    /** Note SRAM can be 32k, but haven't found any carts that use that, so hard coding to 16K */
    private int SRAM_BANKS_TO_SAVE = 16;
        
    private void loadSettings()
    {
        try
        {
            byte[] data = load(FILE_SETTINGS);
            
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            DataInputStream inData = new DataInputStream(in); 
             
            Vdp.videoMode = inData.readBoolean() ? Vdp.NTSC : Vdp.PAL;
            scaleEnabled = inData.readBoolean();
            throttle = inData.readBoolean();
            minSleep = inData.readInt();
            frameSkip = inData.readInt();
            rotate = inData.readInt();           
            soundEnabled = inData.readBoolean();
            
            // Read keymap
            K_UP_MAP = inData.readInt();
            K_DOWN_MAP = inData.readInt();
            K_LEFT_MAP = inData.readInt();
            K_RIGHT_MAP = inData.readInt();
            K_FIRE1_MAP = inData.readInt();
            K_FIRE2_MAP = inData.readInt();
            K_START_MAP = inData.readInt();
            K_UL_MAP = inData.readInt();
            K_UR_MAP = inData.readInt();
            K_DL_MAP = inData.readInt();
            K_DR_MAP = inData.readInt();
            K_MENU_MAP = inData.readInt();
              
            inData.close();
            in.close();  
        }
        catch (Exception e)
        {
            //if (DEBUG)
            //    e.printStackTrace();
        }
    }
    
    public void saveSettings()
    {
        try
        {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); 
            DataOutputStream outData = new DataOutputStream(out);  
    
            outData.writeBoolean(Vdp.videoMode == Vdp.NTSC);
            outData.writeBoolean(scaleEnabled);
            outData.writeBoolean(throttle);
            outData.writeInt(minSleep);
            outData.writeInt(frameSkip);
            outData.writeInt(rotate);
            outData.writeBoolean(soundEnabled);
            
            // Write keymap
            outData.writeInt(K_UP_MAP);
            outData.writeInt(K_DOWN_MAP);
            outData.writeInt(K_LEFT_MAP);
            outData.writeInt(K_RIGHT_MAP);
            outData.writeInt(K_FIRE1_MAP);
            outData.writeInt(K_FIRE2_MAP);
            outData.writeInt(K_START_MAP);
            outData.writeInt(K_UL_MAP);
            outData.writeInt(K_UR_MAP);
            outData.writeInt(K_DL_MAP);
            outData.writeInt(K_DR_MAP);
            outData.writeInt(K_MENU_MAP);
                    
            byte[] data = out.toByteArray();                   
    
            outData.close();
            out.close();   
            
            save(FILE_SETTINGS, data);
        }
        catch (Exception e)
        {
            if (DEBUG)
                e.printStackTrace();
        }
    }
    
    private void loadSRAM()
    {
        try
        {
            byte[] bytes = load(romBaseName + EXT_SRAM);
            
            if (bytes != null)
                z80.setSRAM(bytes);
        }
        catch (Exception e)
        {
            //if (DEBUG)
            //    e.printStackTrace();
        }
    }
    
    public void saveSRAM()
    {
        if (z80 == null || !z80.hasUsedSRAM()) return;
        
        try
        {            
            byte data[] = new byte[SRAM_BANKS_TO_SAVE * Setup.PAGE_SIZE];
            
            for (int i = 0; i < SRAM_BANKS_TO_SAVE; i++)
                System.arraycopy(z80.sram[i], 0, data, i * Setup.PAGE_SIZE, Setup.PAGE_SIZE);
            
            save(romBaseName + EXT_SRAM, data);
        }
        catch (Exception e)
        {
            if (DEBUG)
                e.printStackTrace();
        }        
    }
    
    // --------------------------------------------------------------------------------------------
    // Main Thread
    // --------------------------------------------------------------------------------------------
    
    public synchronized void start() 
    {
        thread = new Thread(this);      
        thread.setPriority(Platform.THREAD_PRIORITY);       
        thread.start();   
    }
    
    public synchronized void stop(boolean waitForClose)
    {
        running = false; // stop thread safely
        
        if (waitForClose)
        {
            while (thread != null && thread.isAlive())
            {
                Thread.yield();
            }
        }
        
        if (SUPPORTS_SOUND && soundEnabled) platformFunction(this, PLATFORM_AUDIO_STOP);
    }

    public void run() 
    {
       running = true;
       
       try
       {
            while (running)
            {
                // --------------------------------------------------------------------------------
                // Main Emulate Loop
                // --------------------------------------------------------------------------------
                if (state == S_EMULATE)
                {
                    if (hasKeyPressed(K_MENU))
                    {
                        initMenu();
                    }
                    
                    // No throttling: faster code if phone is slow
                    else if (!throttle)
                    {
                        if (emulateNextFrame())
                            doRepaint();                     
                        if (minSleep != 0)
                            Thread.sleep(minSleep);
                    }
                    // Throttling, also try a minimum sleep per tick
                    else
                    {
                        long startTime = System.currentTimeMillis();
                        
                        if (emulateNextFrame())
                            doRepaint();
                        
                        if (ID == J2ME)
                        {                       
                            long frameTime = System.currentTimeMillis() - startTime;
                            
                            if (frameTime < targetFrameTime - minSleep)
                            {
                                Thread.sleep(targetFrameTime - frameTime);
                            }
                            else if (minSleep != 0)
                                Thread.sleep(minSleep);
                        }
                        else if (ID == J2SE)
                        {
                            platformFunction(this, PLATFORM_THROTTLE);   
                        }
                    }

                    if (THREAD_YIELD)
                        Thread.yield();               
                }
                
                // --------------------------------------------------------------------------------
                // Menu / UI Loop
                // --------------------------------------------------------------------------------
                else if (state == S_MENU)
                {
                    long startTime = System.currentTimeMillis();
                    
                    tickUI();
                    doRepaint();
                    
                    long frameTime = System.currentTimeMillis() - startTime;
                    
                    if (frameTime < 60 - minSleep)
                        Thread.sleep(60 - frameTime);
                    
                    if (THREAD_YIELD)
                        Thread.yield();
                }
                
                tick++;
                
            } // End of running loop
       }
       catch (Exception e)
       {
           if (DEBUG)
               e.printStackTrace();
       }
    }
    
    /**
     * Generate a random integer.
     * 
     * @param range Generate random numbers from 0 to range. 
     *              A range of 4 would generate numbers between 0 and 3.
     */

    public final static int rndInt(int range) 
    {
        return Math.abs(rnd.nextInt() % range);
    }
    
    // --------------------------------------------------------------------------------------------
    // Emulation Related
    // --------------------------------------------------------------------------------------------

    /** Emulated screen width */
    public static int emuWidth;
    
    /** Emulated screen height */
    public static int emuHeight;
    
    /**
     *  Set SMS Mode
     */

    public final void setSMS()
    {
        is_sms = true;
        is_gg  = false;

        Vdp.h_start = 0;
        Vdp.h_end   = 32;
        
        emuWidth = Vdp.SMS_WIDTH;
        emuHeight = Vdp.SMS_HEIGHT;
    }

    /**
     *  Set GG Mode
     */

    public final void setGG()
    {
        is_gg  = true;
        is_sms = false;
        
        Vdp.h_start = 5;
        Vdp.h_end   = 27;
        
        emuWidth = Vdp.GG_WIDTH;
        emuHeight = Vdp.GG_HEIGHT;
    }
    
    private final void setFrameSkip(int s)
    {
        frameSkip = s;
        targetFrameTime = (1000 / fps);
        
        if (ID == J2SE) platformFunction(this, PLATFORM_FRAMESKIP);
    }

    /**
     *  Set NTSC/PAL Timing
     *  
     *  Exact timings from:
     *  http://www.smspower.org/dev/docs/wiki/Systems/MasterSystem
     */

    public final void setVideoTiming(int mode)
    {
        int clockSpeedHz = 0;
        
        // Game Gear should only work in NTSC
        if (mode == Vdp.NTSC || is_gg)
        {
            fps = 60;
            no_of_scanlines = Vdp.SMS_Y_PIXELS_NTSC;          
            clockSpeedHz = CLOCK_NTSC;
        }
        else if (mode == Vdp.PAL)
        {
            fps = 50;
            no_of_scanlines = Vdp.SMS_Y_PIXELS_PAL;          
            clockSpeedHz = CLOCK_PAL;   
        }
        
        // Add one manually here for rounding accuracy
        cyclesPerLine = (clockSpeedHz / fps / no_of_scanlines) + 1;
        Vdp.videoMode = mode;
        
        // Setup appropriate sound buffer
        if (SUPPORTS_SOUND)
        {
            psg.init(clockSpeedHz, SAMPLE_RATE);
            
            samplesPerFrame = SAMPLE_RATE / fps;
        
            if (audioBuffer == null || audioBuffer.length != samplesPerFrame)
                audioBuffer = new byte[samplesPerFrame];
            
            if (samplesPerLine == null || samplesPerLine.length != no_of_scanlines)
            {
                samplesPerLine = new int[no_of_scanlines];

                int fractional = 0;
                
                // Calculate number of sound samples to generate per scanline
                for (int i = 0; i < no_of_scanlines; i++)
                {
                    int v = ((samplesPerFrame << 16) / no_of_scanlines) + fractional;
                    fractional = v - ((v >> 16) << 16);    
                    samplesPerLine[i] = v >> 16;
                }
            }
        }
        
        setFrameSkip(frameSkip);
    }

    public static long z80Time;
    public static long drawTime; 
    
    private long z80TimeCounter;
    private long drawTimeCounter; 
    
    private int frameCount;

    
    private final boolean emulateNextFrame()
    {   
        // Draw one frame
        for (int lineno = 0; lineno < no_of_scanlines; lineno++)
        {   
            long startTime;
                 
            if (Setup.DEBUG_TIMING) startTime = System.currentTimeMillis();          
            
            // ------------------------------------------------------------------------------------
            // Run Z80
            //
            // Ensure interrupts always occur, and vblank is taken between instructions
            // If the IRQ status flag is set *during* the execution of an instruction the 
            // CPU will be able to read it without the interrupt occurring. 
            //
            // For example, "IN A,($BF)" is 11 T-states. If bit 7 of the status flags is reset prior 
            // the instruction being fetched and executed, but then set 2 T-states into execution, 
            // then the value read from the I/O port will have bit 7 set.
            // ------------------------------------------------------------------------------------
            
            if (Setup.ACCURATE_INTERRUPT_EMULATION && lineno == 193)
            {
                z80.run(cyclesPerLine, 8);  // Run until 8 cycles remaining
                vdp.setVBlankFlag();        // Then set VBlank flag
                z80.run(0, 0);              // Run for remaining 8 cycles
            }
            else
            {
                z80.run(cyclesPerLine, 0);
            }
            
            if (Setup.DEBUG_TIMING) z80TimeCounter += System.currentTimeMillis() - startTime;

            // ------------------------------------------------------------------------------------
            // PSG
            // ------------------------------------------------------------------------------------
            
            if (Platform.SUPPORTS_SOUND && Engine.soundEnabled)
                updateSound(lineno);
            
            // ------------------------------------------------------------------------------------
            // VDP
            // ------------------------------------------------------------------------------------
            
            vdp.line = lineno;
            
            // Draw Next Line
            if (frameskip_counter == 0 && lineno < 192)
            {
                if (Setup.DEBUG_TIMING) startTime = System.currentTimeMillis();             
                vdp.drawLine(lineno);               
                if (Setup.DEBUG_TIMING) drawTimeCounter += System.currentTimeMillis() - startTime;
            } 
                        
            // Assert Interrupt Line if Necessary
            vdp.interrupts(lineno);
        }

        if (SUPPORTS_SOUND && soundEnabled)
            audioOutput(audioBuffer);
        
        // Reset framecount once we've drawn 60 frames per second
        if (Setup.DEBUG_TIMING && ++frameCount == 60)
        {
            z80Time = z80TimeCounter;
            drawTime = drawTimeCounter;
            
            z80TimeCounter = 0;
            drawTimeCounter = 0;
            
            frameCount = 0;
        }
        
        // Only Check for Pause Button once per frame to increase emulation speed
        if (pause_button)
        {
            z80.nmi();
            pause_button = false;
        }
        
        if (frameskip_counter-- == 0)
        {
            frameskip_counter = frameSkip;           
            updateDisplay();          
            return true;
        }
        return false;
    }
    
    /**
     * Update the emulated display
     * 
     * 1/ Rotate
     * 2/ Scale
     * 3/ Set Relevant Pixel Array to render
     */
    private final void updateDisplay()
    {
        if (rotate == 0)
        {        
            // Scale image to fit screen with internal code
            if (screenMode != SM_NORMAL)
            {
                scale(display, Vdp.SMS_WIDTH, Vdp.SMS_HEIGHT, 
                      sourceOffset, sourceWidth, sourceHeight,
                      scaled, scaledWidth, scaledHeight);
                render = scaled;
            }
            else
                render = display;
        } 
        else
        {
            if (rotate == 180)
            {
                rotate180(display);
                if (screenMode != SM_NORMAL)
                {
                    scale(rotatedDisplay, Vdp.SMS_WIDTH, Vdp.SMS_HEIGHT, 
                          sourceOffset, sourceWidth, sourceHeight, 
                          scaled, scaledWidth, scaledHeight);
                    render = scaled;
                }
                else
                    render = rotatedDisplay;
            }
            // rotate is 90 or 270
            else
            {
                if (rotate == 90) rotate90(display);
                else if (rotate == 270) rotate270(display);
                
                if (screenMode != SM_NORMAL)
                {
                    scale(rotatedDisplay, Vdp.SMS_HEIGHT, Vdp.SMS_WIDTH, 
                          sourceOffset, sourceHeight, sourceWidth, 
                          scaled, scaledWidth, scaledHeight);
                    render = scaled;
                }
                else
                    render = rotatedDisplay;
            }
        }
    }
    
    private final void updateSound(int line)
    {
        if (line == 0)
            audioBufferOffset = 0;
        
        int samplesToGenerate = samplesPerLine[line];       
        psg.update(audioBuffer, audioBufferOffset, samplesToGenerate);  
        audioBufferOffset += samplesToGenerate;
    }
        
    
    // --------------------------------------------------------------------------------------------
    // Controllers
    // --------------------------------------------------------------------------------------------
    
    /** Controller 1 */
    public static int controller1;
    
    /** Controller 2 */
    public static int controller2;
    
    /** Game Gear Start Button */
    public static int ggstart;
    
    /** Lightgun position */
    public static int lightgunX, lightgunY;
    
    /** Lightgun button pressed */
    public static boolean lightgunClick;
    
    /** Lightgun is enabled */
    public static boolean lightgunEnabled;

    /**
     *  Reset controllers to default state
     *
     */

    public final static void resetControllers()
    {
        // Default 0xFF = No Keys Pressed
        controller1 = 0xFF;
        controller2 = 0xFF;
        ggstart = 0xFF;
        
        // Turn lightgun off
        if (Setup.LIGHTGUN)
        {
            lightgunClick = false;
        }
        
        pause_button = false;
    }

    /**
     *  Key Pressed
     *
     *  @param evt  Key Code
     */

    public final static void keyPress(int keyCode)
    {        
        key = keyCode;

        if ((keyCode & K_UP) != 0)          controller1 &= ~0x01;    // Up
        else if ((keyCode & K_DOWN) != 0)   controller1 &= ~0x02;    // Down
        if ((keyCode & K_LEFT) != 0)    controller1 &= ~0x04;    // Left
        else if ((keyCode & K_RIGHT) != 0)    controller1 &= ~0x08;    // Right
        if ((keyCode & K_FIRE1) != 0)   controller1 &= ~0x10;   // Fire 1
        if ((keyCode & K_FIRE2) != 0)   controller1 &= ~0x20;   // Fire 2
        else if (keyCode == K_START)
        {
            if (is_sms)
                //controller2 &= ~0x10; // Reset
                pause_button = true; // Pause
            else
                ggstart     &= ~0x80;   // Start
        }
    }

    /**
     *  Key Released
     *
     *  @param evt  Key Code
     */

    public final static void keyRelease(int keyCode)
    {
        if ((keyCode & K_UP) != 0)          controller1 |= 0x01;    // Up
        else if ((keyCode & K_DOWN) != 0)   controller1 |= 0x02;    // Down
        if ((keyCode & K_LEFT) != 0)    controller1 |= 0x04;    // Left
        else if ((keyCode & K_RIGHT) != 0)  controller1 |= 0x08;    // Right
        if ((keyCode & K_FIRE1) != 0)   controller1 |= 0x10;    // Fire 1
        if ((keyCode & K_FIRE2) != 0)   controller1 |= 0x20;    // Fire 2
        else if (keyCode == K_START)
        {
            if (!is_sms)
            //  controller2 |= 0x10;    // Reset/Start
            //else
                ggstart     |= 0x80;    // Start
        }
    }
    
    private final boolean hasKeyPressed(int k)
    {
        if (k == key)
        {
            key = 0;
            return true;
        }
        return false;
    }
    
    public final static int getKeyMap(int key)
    {               
        if (Engine.state == Engine.S_MENU)
        {   
            if (key == K_MENU_MAP && uiState == UI_PRESSKEY) 
                return Engine.K_MENU;
            
            if (key == K_MENU_UP_MAP)
                return Engine.K_UP;
                    
            if (key == K_MENU_DOWN_MAP)
                return Engine.K_DOWN;
            
            if (key == K_MENU_SELECT_MAP)
                return Engine.K_FIRE1;
        }
        else
        {    
            // Always have this first so the user can get into the menu no matter their key defines
            if (key == K_MENU_MAP) 
                return Engine.K_MENU;
            
            if (key == K_UP_MAP) 
                return Engine.K_UP;
            
            if (key == K_DOWN_MAP)
                return Engine.K_DOWN;
                    
            if (key == K_LEFT_MAP)
                return Engine.K_LEFT;
                    
            if (key == K_RIGHT_MAP)
                return Engine.K_RIGHT;  
            
            if (key == K_UL_MAP) 
                return Engine.K_UP | Engine.K_LEFT;
            
            if (key == K_UR_MAP) 
                return Engine.K_UP | Engine.K_RIGHT;
            
            if (key == K_DL_MAP) 
                return Engine.K_DOWN | Engine.K_LEFT;
            
            if (key == K_DR_MAP) 
                return Engine.K_DOWN | Engine.K_RIGHT;
            
            if (key == K_FIRE1_MAP) 
                return Engine.K_FIRE1;
            
            if (key == K_FIRE2_MAP) 
                return Engine.K_FIRE2;
            
            if (key == K_START_MAP) 
                return Engine.K_START;
        }

        return 0;
    }
    
    public final static void setLightGunPos(int x, int y)
    {
        if (Setup.LIGHTGUN && is_sms && lightgunEnabled)
        {
            // Check bounds of position in range
            if (x < renderX || x > renderX + renderWidth ||
                y < renderY || y > renderY + renderHeight)
            {
                lightgunClick = false;
                controller1 |= 0x10;
                return;
            }
            
            // Scale mouse click based on screen stretch or shrink
            if (screenMode != SM_NORMAL)
            {
                int wMod = (renderWidth << SHIFT) / (rotate == 0 || rotate == 180 ? emuWidth : emuHeight);
                int hMod = (renderHeight << SHIFT) / (rotate == 0 || rotate == 180 ? emuHeight : emuWidth);
                
                lightgunX = ((x - renderX) << SHIFT) / wMod;
                lightgunY = ((y - renderY) << SHIFT) / hMod;
            }
            else
            {
                // Absolute X/Y position
                int absX = (screen_width - (rotate == 0 || rotate == 180 ? emuWidth : emuHeight)) >> 1;
                int absY = (screen_height - (rotate == 0 || rotate == 180 ? emuHeight : emuWidth)) >> 1;   
                
                lightgunX = x - absX;
                lightgunY = y - absY;
            }  
            
            // Rotate lightgun position if necessary
            if (rotate != 0)
            {   
                if (rotate == 90)
                {                
                    int newX = lightgunY;
                    int newY = Vdp.SMS_HEIGHT - lightgunX;
                    
                    lightgunX = newX;
                    lightgunY = newY;
                }
                else if (rotate == 180)
                {
                    lightgunX = Vdp.SMS_WIDTH - lightgunX;
                    lightgunY = Vdp.SMS_HEIGHT - lightgunY;
                }
                // rotate is 270
                else
                {
                    int newX = Vdp.SMS_WIDTH - lightgunY;
                    int newY = lightgunX;
                    
                    lightgunX = newX;
                    lightgunY = newY;                   
                }              
            }
       
            // On phones, allow user to touch screen to load menu
            if (ID == J2ME && Engine.state == Engine.S_MENU)
                key = K_FIRE1;
            else
            {
                controller1 &= ~0x10;
                lightgunClick = true;
            }
        }
    }
    
    // --------------------------------------------------------------------------------------------
    // UI / Menu Code
    // --------------------------------------------------------------------------------------------
        
    /** Strings to display in menu */
    private String[] menuStrings;
    
    /** Menu cursor position */
    private int menuCursor;
    
    /** Colours */
    private final static int 
        COLOUR_HIGHLIGHT = 0x0000AA,
        COLOUR_HEADER = 0x800000,
        COLOUR_HEADER_DARK = 0x400000,
        COLOUR_BLACK = 0;
    
    /** UI State */
    private static int uiState;
    
    private final static int
        UI_MENU_MAIN = 0,       // Main Menu
        UI_MENU_REDEFINE = 1,   // Redefine Keys Menu
        UI_REDEFINE_KEYS = 2,   // Get New Key
        UI_MENU_SETTINGS = 3,   // Settings Menu
        UI_STARTUP = 4,
        UI_PRESSKEY = 5,
        UI_MESSAGE = 6;         // Print message
    
    /** Update screen settings when menu is dismissed - optimisation */
    private boolean updateScreenSettings;
        
    public final void paintUI(Gfx g)
    {
        switch (uiState)
        {
            case UI_MENU_MAIN:
            case UI_MENU_REDEFINE:
            case UI_REDEFINE_KEYS:
            case UI_MENU_SETTINGS:
                paintMenu(g);
                break;
                
            case UI_STARTUP:
            case UI_PRESSKEY:
                paintStartup(g);
                break;
                
            case UI_MESSAGE:
                paintMessage(g);
                break;
        }
    }
     
    public final void tickUI()
    {
        switch (uiState)
        {
            case UI_MENU_MAIN:
            case UI_MENU_REDEFINE:
            case UI_MENU_SETTINGS:
                tickMenu();
                break;
                
            case UI_REDEFINE_KEYS:
                tickMenuKeys(menuCursor);
                break;

            case UI_STARTUP:
                init();
                break;
                
            case UI_PRESSKEY:
                if (hasKeyPressed(K_FIRE1) || hasKeyPressed(K_FIRE2))
                    unloadMenu();
                else if (hasKeyPressed(K_MENU))
                    initMenu();
                break;
                
            case UI_MESSAGE:
                if (hasKeyPressed(K_FIRE1) || hasKeyPressed(K_FIRE2))
                {
                    // Messages during startup indicate a failure, so quit before things get messy
                    if (prevState == UI_STARTUP)
                        exit();
                    // Otherwise continue whatever we were doing before
                    else
                        uiState = prevState;
                }
                break;
        }
    }
    
    private final void paintMenu(Gfx g)
    {
        // Clear screen to black
        cls(g, COLOUR_BLACK);
        
        paintHeader(g);

        int yInc = FONT_SIZE + 1;      
        int y = (screen_height - (menuStrings.length * yInc)) >> 1;
           
        // Flash lightbar
        boolean flash = (tick & 2) == 0 && uiState == UI_REDEFINE_KEYS;
            
        // Paint menu options
        for (int i = 0; i < menuStrings.length; i++)
        {    
            String s = menuStrings[i];
            
            if (!flash && i == menuCursor)
            {      
                g.setColor(COLOUR_HIGHLIGHT);
                g.fillRect(0, y, screen_width, FONT_SIZE);
            }
            
            if (s != null)
                paintString(g, menuStrings[i], screen_width >> 1, y, Gfx.TOP | Gfx.HCENTER);  
            
            y += yInc;
        }         
    }
    
    private final void tickMenu()
    {
        if (hasKeyPressed(K_DOWN))
        {
            menuCursor++;
            
            // Advance if position is null
            if (menuCursor < menuStrings.length && menuStrings[menuCursor] == null)
                menuCursor++;
            
            if (menuCursor >= menuStrings.length)
                menuCursor = 0;
        }
        else if (hasKeyPressed(K_UP))
        {
            menuCursor--;
            
            // Decrease if position is null
            if (menuCursor > 0 && menuStrings[menuCursor] == null)
                menuCursor--;
            
            if (menuCursor < 0)
                menuCursor = menuStrings.length - 1;
        }
        else if (hasKeyPressed(K_FIRE1))
        {
            doMenuItem(menuCursor);
        }
    }
        
    private final void paintStartup(Gfx g)
    {
        cls(g, COLOUR_BLACK);
                
        int y = 0;
        
        // Paint header
        paintHeader(g);
                
        g.setColor(COLOUR_HEADER_DARK);
        g.fillRect(0, y + FONT_SIZE, screen_width, (FONT_SIZE * 6));
        
        // Paint memory information
        y += (FONT_SIZE << 1);
        
        System.gc();
        long totalMem = Runtime.getRuntime().totalMemory() >> 10;
        long freeMem = Runtime.getRuntime().freeMemory() >> 10;

        paintString(g, "TOTAL MEMORY: "+totalMem+"K", screen_width >> 1, y, Gfx.TOP | Gfx.HCENTER);
        y += FONT_SIZE;
        
        paintString(g, "FREE MEMORY : "+freeMem+"K", screen_width >> 1, y, Gfx.TOP | Gfx.HCENTER);
        y += (FONT_SIZE << 1);
        
        // @ = (C) in font we're using
        paintString(g, "@ CHRIS WHITE 2008", screen_width >> 1, y, Gfx.TOP | Gfx.HCENTER);
        y += FONT_SIZE;
        paintString(g, "WWW.JAVAGEAR.CO.UK", screen_width >> 1, y, Gfx.TOP | Gfx.HCENTER);
        
        y += (FONT_SIZE << 1);
        
        // Paint console output
        consolePaint(g, y, Gfx.TOP | Gfx.LEFT);
    }
    
    private final void paintHeader(Gfx g)
    {
        g.setColor(COLOUR_HEADER);
        g.fillRect(0, 0, screen_width, FONT_SIZE);
        paintString(g, new String(Setup.PROGRAM_NAME+" "+BuildSettings.VERSION).toUpperCase(), screen_width >> 1, 0, Gfx.TOP | Gfx.HCENTER);   
    }
    
    public final static void cls(Gfx g, int c)
    {
        g.setColor(c);
        g.fillRect(0, 0, screen_width, screen_height);   
    }
    
    /** Clear screen and repaint immediately. Used on J2ME version */
    private final void clsRepaint()
    {
        int s = state;
        state = S_CLS;
        doRepaint();
        state = s;
    }
    
    
    // --------------------------------------------------------------------------------------------
    // Menu: Main Menu
    // --------------------------------------------------------------------------------------------
    
    /** Labels for main menu */
    private final static String 
        MENU_STRING_RETURN = "RETURN TO GAME",
        MENU_STRING_LOAD_STATE = "LOAD STATE",
        MENU_STRING_SAVE_STATE = "SAVE STATE",
        MENU_STRING_SLOT = "SLOT: ",
        MENU_STRING_SOUND = "SOUND: ",
        MENU_STRING_SETTINGS = "SETTINGS",
        MENU_STRING_RESET = "HARD RESET", 
        MENU_STRING_EXIT = "EXIT"; 
         
    public final void initMenu()
    {    
        if (SUPPORTS_SOUND && soundEnabled) platformFunction(this, PLATFORM_AUDIO_STOP);
        
        updateMenuMain();
        
        menuCursor = 0;
        updateScreenSettings = false;
        
        uiState = UI_MENU_MAIN;
        state = S_MENU;
    }
    
    private final void updateMenuMain()
    {
        if (SUPPORTS_SOUND)
        {
            menuStrings = new String[] 
            { 
                    MENU_STRING_RETURN,
                    null,
                    MENU_STRING_LOAD_STATE,
                    MENU_STRING_SAVE_STATE,
                    MENU_STRING_SLOT + slot,
                    null,
                    MENU_STRING_SOUND + (soundEnabled ? "ON" : "OFF"),
                    MENU_STRING_SETTINGS,
                    null,
                    MENU_STRING_RESET, 
                    MENU_STRING_EXIT, 
            };  
        }
        else
        {
            menuStrings = new String[] 
            { 
                    MENU_STRING_RETURN,
                    null,
                    MENU_STRING_LOAD_STATE,
                    MENU_STRING_SAVE_STATE,
                    MENU_STRING_SLOT + slot,
                    null,
                    MENU_STRING_SETTINGS,
                    null,
                    MENU_STRING_RESET, 
                    MENU_STRING_EXIT, 
            };              
        }
    }
    
    private final void unloadMenu()
    {
        clsRepaint();
                
        updateDisplay();
        
        saveSettings();
        state = S_EMULATE;
        resetControllers();
        key = 0;
        
        if (SUPPORTS_SOUND && soundEnabled) platformFunction(this, PLATFORM_AUDIO_START);
        if (ID == J2SE) platformFunction(this, PLATFORM_THROTTLE_INIT);
    }
    
    private final void doMenuItem(int index)
    {
        if (uiState == UI_MENU_MAIN)
        {
            String option = menuStrings[index];
            
            // RETURN TO GAME
            if (option == MENU_STRING_RETURN)
            {
                setFrameSkip(frameSkip);  
                unloadMenu();
            }
            // LOAD STATE
            else if (option == MENU_STRING_LOAD_STATE)
            {
                if (loadState())
                    unloadMenu();
                else
                    setMessage("ERROR LOADING STATE "+slot);               
            }
            // SAVE STATE
            else if (option == MENU_STRING_SAVE_STATE)
            {
                if (saveState())
                    unloadMenu();
                else
                    setMessage("ERROR SAVING STATE "+slot);                 
            }
            // TOGGLE SAVE SLOT
            else if (option.startsWith(MENU_STRING_SLOT))
            {
                if (++slot >= NUMBER_OF_SLOTS)
                    slot = 0;
                updateMenuMain();
            }
            // TOGGLE SOUND
            else if (option.startsWith(MENU_STRING_SOUND))
            {
                soundEnabled = !soundEnabled;
                updateMenuMain();
            }
            // SETTINGS
            else if (option == MENU_STRING_SETTINGS)
            {
                setMenuSettings();
            }
            // HARD RESET
            else if (option == MENU_STRING_RESET)
            {
                reset();
                unloadMenu();
            }
            // EXIT
            else if (option == MENU_STRING_EXIT)
            {
                exit();
            }
        }
        else if (uiState == UI_MENU_SETTINGS)
        {
            switch (index)
            {
                // RETURN TO MAIN MENU
                case 0:
                    if (updateScreenSettings)
                    {
                        setupScreen(scaleEnabled);           
                        updateScreenSettings = false;
                    }
                    initMenu();
                    break;
                                         
                // TOGGLE NTSC/PAL
                case 2:
                    setVideoTiming(Vdp.videoMode ^ 1);
                    updateMenuSettings();
                    break;
                    
                // TOGGLE SCREEN SCALING
                case 3:
                    scaleEnabled = !scaleEnabled;
                    updateScreenSettings = true;
                    updateMenuSettings();
                    break;
                    
                // ROTATE
                case 4:
                    rotate += 90;
                    if (rotate > 270) rotate = 0;
                    updateScreenSettings = true;
                    updateMenuSettings();
                    break;
                    
                // FRAMESKIP
                case 5:
                    if (++frameSkip > 4)
                        frameSkip = 0;
                    updateMenuSettings();
                    break;
                    
                // THROTTLE
                case 6:
                    throttle = !throttle;
                    updateMenuSettings();
                    break;
                    
                // MINIMUM SLEEP
                case 7:
                    if (minSleep == 0) minSleep = 3;
                    else if (minSleep == 3) minSleep = 5;
                    else if (minSleep == 5) minSleep = 10;
                    else minSleep = 0;
                    updateMenuSettings();
                    break;
                    
                // REDEFINE KEYS
                case 9:
                    setMenuKeys();
                    break;
            }                
        }
        else if (uiState == UI_MENU_REDEFINE)
        {
            // Exit menu
            if (menuCursor == 0)
                setMenuSettings();
            // Redefine key
            else
            {   
                uiState = UI_REDEFINE_KEYS;
                keyCode = 0;
            } 
        }
    }
    
    // --------------------------------------------------------------------------------------------
    // Menu: Settings
    // --------------------------------------------------------------------------------------------
      
    private final void setMenuSettings()
    {
        updateMenuSettings();
        menuCursor = 0;      
        uiState = UI_MENU_SETTINGS;       
    }
    
    private final void updateMenuSettings()
    {
        menuStrings = new String[] 
        { 
                 "RETURN TO MAIN MENU",
                 null,
                 new String("VIDEO: " + (Vdp.videoMode == Vdp.NTSC ? "NTSC" : "PAL")),
                 new String("SCALE: " + scaleEnabled).toUpperCase(),
                 new String("ROTATE: "+rotate),
                 "FRAMESKIP: " + frameSkip, 
                 new String("THROTTLE: " + throttle).toUpperCase(), 
                 "MIN SLEEP: " + minSleep, 
                 null,
                 "DEFINE KEYS",
        };  
    }
    
    // --------------------------------------------------------------------------------------------
    // Menu: Redefine Keys
    // --------------------------------------------------------------------------------------------

    private final void setMenuKeys()
    {
        updateMenuKeys();   
        menuCursor = 0;      
        uiState = UI_MENU_REDEFINE;
    }
    
    private final void tickMenuKeys(int index)
    {
        if (keyCode != 0)
        { 
            switch (index)
            {                
                case 2: K_UP_MAP = keyCode; break;
                case 3: K_DOWN_MAP = keyCode; break;
                case 4: K_LEFT_MAP = keyCode; break;
                case 5: K_RIGHT_MAP = keyCode; break;
                case 6: K_FIRE1_MAP = keyCode; break;
                case 7: K_FIRE2_MAP = keyCode; break;
                case 8: K_START_MAP = keyCode; break;
                case 9: K_UL_MAP = keyCode; break;
                case 10: K_UR_MAP = keyCode; break;
                case 11: K_DL_MAP = keyCode; break;
                case 12: K_DR_MAP = keyCode; break;
                
                case 14: K_MENU_MAP = keyCode; break;
            }
            
            updateMenuKeys();     
            key = 0;
            uiState = UI_MENU_REDEFINE;
        }
    }
    
    private final void updateMenuKeys()
    {
        menuStrings = new String[] 
         { 
                "RETURN TO SETTINGS",            
                null,
                "UP: "+getKeyNm(K_UP_MAP),
                "DOWN: "+getKeyNm(K_DOWN_MAP),
                "LEFT: "+getKeyNm(K_LEFT_MAP),
                "RIGHT: "+getKeyNm(K_RIGHT_MAP),
                "FIRE 1: "+getKeyNm(K_FIRE1_MAP),
                "FIRE 2: "+getKeyNm(K_FIRE2_MAP),
                "START/PAUSE: "+getKeyNm(K_START_MAP),
                "UP-LEFT: "+getKeyNm(K_UL_MAP),
                "UP-RIGHT: "+getKeyNm(K_UR_MAP),
                "DN-LEFT: "+getKeyNm(K_DL_MAP),
                "DN-RIGHT: "+getKeyNm(K_DR_MAP),
                null,
                "MENU KEY: "+getKeyNm(K_MENU_MAP)
         };           
    }
        
    // --------------------------------------------------------------------------------------------
    // Print Simple Message to Screen 
    // --------------------------------------------------------------------------------------------
    
    /** Holds previous UI state when returning from error */
    private int prevState;
    
    public final void setMessage(String s)
    {
        setMessage(new String[] {s});
    }
    
    private final void setMessage(String[] s)
    {
        consoleClear();
        
        for (int i = 0; i < s.length; i++)
            consolePrint(s[i].toUpperCase(), false);
        
        prevState = uiState;
        uiState = UI_MESSAGE;
    }
    
    private final void paintMessage(Gfx g)
    {
        cls(g, COLOUR_BLACK);  
        // Paint header
        paintHeader(g);                     
        // Paint console output
        consolePaint(g, (screen_height - (consolePosition * FONT_SIZE)) >> 1, Gfx.TOP | Gfx.HCENTER);
    }
    
    // --------------------------------------------------------------------------------------------
    // Console / Window output
    // --------------------------------------------------------------------------------------------
    
    /** Strings to print to console window */
    private static String[] consoleStrings;
    
    /** Position to write to console */
    private static int consolePosition;

    public final void consolePrint(String s, boolean refresh)
    {
        if (consoleStrings == null)
        {
            consoleStrings = new String[(screen_height - (FONT_SIZE * 2)) / FONT_SIZE];
        }
        
        // Move strings up one position
        if (consolePosition == consoleStrings.length - 1)
        {
            System.arraycopy(consoleStrings, 1, consoleStrings, 0, consoleStrings.length - 1);
        }
        
        // Insert new string
        consoleStrings[consolePosition] = s;
                       
        // Increment console position if possible
        if (consolePosition < consoleStrings.length - 1)
            consolePosition++;
        
        if (refresh)
            doRepaint();
    }
    
    public final void consolePrintDivider()
    {
        StringBuffer sb = new StringBuffer();
        
        int numberChars = screen_width / FONT_SIZE;
        
        for (int i = numberChars; i-- != 0;)
            sb.append('-');
        
        consolePrint(sb.toString(), false);
    }
    
    public final void consoleClear()
    {
        consolePosition = 0;
    }
    
    private final void consolePaint(Gfx g, int y, int anchor)
    {
        int x = (anchor & Gfx.HCENTER) != 0 ? screen_width >> 1 : 2;
        
        for (int i = 0; i < consolePosition; i++)
        {
            paintString(g, consoleStrings[i], x, y, anchor);
            y += FONT_SIZE;
        }
    }
    
    // --------------------------------------------------------------------------------------------
    // Bitmap Font Handling
    // --------------------------------------------------------------------------------------------
    
    /** Pixel size of bitmap font */
    private final static int FONT_SIZE = 8;
    
    /** Font image */
    private PlatformImg[] font;
    
    private final void paintString(Gfx g, String s, int x, int y, int anchor)
    {
        char[] chars = s.toCharArray();
        
        if ((anchor & Gfx.HCENTER) != 0)
            x -= (chars.length * FONT_SIZE) >> 1;
        
        for (int i = 0; i < chars.length; i++)
        {
            int c = chars[i];
            
            // See http://www.asciitable.com/
            if (c >= 33 && c <= 90)
            {
                font[c-33].drawImage(g, x, y);
            }
            
            x += FONT_SIZE;
        }
    }
    
    private final PlatformImg[] divideImage(PlatformImg i, int w, int h)
    {
        int rows = i.width/w;
        int columns = i.height/h;

        PlatformImg divided[] = new PlatformImg[columns * rows];
                
        int index = 0;
            
        for (int y = 0; y < columns; y++)
            for (int x = 0; x < rows; x++)
                divided[index++] = new PlatformImg(i, x*w, y*h, w, h);                       
        
        return divided;
    }
    
    // -------------------------------------------------------------------------------------------- //
    // Image Rendering & Scaling Code
    //--------------------------------------------------------------------------------------------- //

    /** Is scaling enabled */
    private boolean scaleEnabled = DEFAULT_SCALE;

    /** Width and height of source area to scale */
    private int sourceWidth, sourceHeight, sourceOffset;
    
    /** Width and height of scaled image */
    public static int scaledWidth, scaledHeight;
    
    /** Co-ordinates to render final image */
    public static int renderX, renderY;
    
    /** Width and height of final area to render to canvas */
    public static int renderWidth, renderHeight;
    
    /** Offset into final image to render (used to centre screen) */
    public static int renderOffset;
    
    /** Scaled image data */
    public static int[] scaled;
    
    public final static int SHIFT = 8;
    
    /** Screen Mode */
    public static int screenMode;
    
    public final static int
        SM_NORMAL = 0,
        SM_STRETCH = 1,
        SM_SHRINK = 2;
    
    public final void setupScreen(boolean scaleToFit)
    {
        // ----------------------------------------------------------------------------------------
        // Setup Rotation
        // ----------------------------------------------------------------------------------------
        
        boolean rot = rotate == 90 || rotate == 270;
        
        // Create rotated display
        rotatedDisplay = rotate == 0 ? null : new int[Vdp.SMS_WIDTH * Vdp.SMS_HEIGHT];

        // ----------------------------------------------------------------------------------------
        // Setup default 1:1 scale settings
        // ----------------------------------------------------------------------------------------
        
        int percentage = 100 << SHIFT; // Defalt scale is 100% (original size)

        screenMode = SM_NORMAL;
        scaledWidth = !rot? Vdp.SMS_WIDTH : Vdp.SMS_HEIGHT;
        scaledHeight = !rot ? Vdp.SMS_HEIGHT : Vdp.SMS_WIDTH;
        int scaledGGWidth = !rot ? Vdp.GG_WIDTH : Vdp.GG_HEIGHT;
        int scaledGGHeight = !rot ? Vdp.GG_HEIGHT : Vdp.GG_WIDTH;

        // ----------------------------------------------------------------------------------------
        // Setup Scaled settings, if necessary
        // ----------------------------------------------------------------------------------------
        
        if (scaleToFit)
        {
            int percentageWidth = percentage;
            int percentageHeight = percentage;

            int targetWidth = !rot ? emuWidth : emuHeight;
            int targetHeight = !rot ? emuHeight : emuWidth;

            // True if we need to shrink the display to fit screen
            boolean shrink = (screen_width < targetWidth) || (screen_height < targetHeight);
            
            // True if we need to stretch the display to fit screen
            boolean stretch = !shrink && (screen_width > targetWidth || screen_height > targetHeight);
                        
            if (shrink)
            {
                if (screen_width < targetWidth)
                    percentageWidth = ((screen_width << SHIFT) / targetWidth) * 100;
                              
                if (screen_height < targetHeight)
                    percentageHeight = ((screen_height << SHIFT) / targetHeight) * 100;
                
                screenMode = SM_SHRINK;
            }
            else if (stretch)
            {
                if (screen_width > targetWidth)
                    percentageWidth = ((screen_width << SHIFT) / targetWidth) * 100;
                              
                if (screen_height > targetHeight)
                    percentageHeight = ((screen_height << SHIFT) / targetHeight) * 100;
                
                screenMode = SM_STRETCH;
            }
            
            // Percentage is not set to 100% - resize
            if (percentageWidth >> SHIFT != 100 || percentageHeight >> SHIFT != 100)
            {
                percentage = Math.min(percentageWidth, percentageHeight);
                
                scaledWidth = (((scaledWidth * percentage)) / 100) >> SHIFT;
                scaledHeight = (((scaledHeight * percentage)) / 100) >> SHIFT;
                
                scaledGGWidth = (((scaledGGWidth * percentage)) / 100) >> SHIFT;
                scaledGGHeight = (((scaledGGHeight * percentage)) / 100) >> SHIFT;
                
                if (is_sms)
                {
                    scaled = new int[scaledWidth * scaledHeight];
                }
                else
                {
                    scaled = new int[scaledGGWidth * scaledGGHeight];
                    scaledWidth = scaledGGWidth;
                    scaledHeight = scaledGGHeight;
                }
            }
            else
            {
                screenMode = SM_NORMAL;
            }
        }
        
        // ------------------------------------------------------------------------------------
        // This is a non-scaled Game Gear display
        //
        // This is rendered by just plucking the central Game Gear window out of the 
        // usual 256x192 sized display.
        //
        // GG: Centralise and set window in centre of full sms display
        // ------------------------------------------------------------------------------------
        if (is_gg && screenMode == SM_NORMAL)
        {
            // Get correct offset into screen array
            int xOff = ((( (!rot ? Vdp.GG_X_OFFSET : Vdp.GG_Y_OFFSET) * percentage)) / 100) >> SHIFT;
            int yOff = ((( (!rot ? Vdp.GG_Y_OFFSET : Vdp.GG_X_OFFSET) * percentage)) / 100) >> SHIFT;
            renderOffset = xOff + (yOff * scaledWidth);
            
            // Setup width and height of region to render
            renderWidth = Math.min(scaledGGWidth, screen_width);
            renderHeight = Math.min(scaledGGHeight, screen_height);
            
            // Setup source width and height of region to render
            // Note we're plucking the image from the larger SMS sized screen in this case.
            sourceWidth = Vdp.SMS_WIDTH;
            sourceHeight = Vdp.SMS_HEIGHT;
            sourceOffset = 0;
        }
        
        // ------------------------------------------------------------------------------------
        // This is a scaled Game Gear display
        //
        // GG: The resized area ONLY contains the active GG display
        // We've already omitted the surrounding stuff
        // This essentially means a faster resize on the Game Gear
        // ------------------------------------------------------------------------------------
        else if (is_gg)
        {
            renderOffset = Math.max(0, (scaledWidth - screen_width) >> 1);
            
            // Setup width and height of region to render
            renderWidth = Math.min(scaledWidth, screen_width);
            renderHeight = Math.min(scaledHeight, screen_height); 
            
            // Setup source width and height of region to render
            sourceWidth = Vdp.GG_WIDTH;
            sourceHeight = Vdp.GG_HEIGHT;
            
            // Source offset will depend on whether screen is rotated in this case
            if (rotate == 90)
            {
                int newX = Vdp.SMS_HEIGHT - (Vdp.GG_Y_OFFSET + Vdp.GG_HEIGHT);
                int newY = Vdp.GG_X_OFFSET;
                sourceOffset = newX + (newY * Vdp.SMS_HEIGHT);
            }
            else if (rotate == 270)
            {
                int newX = Vdp.GG_Y_OFFSET;
                int newY = Vdp.SMS_WIDTH - (Vdp.GG_X_OFFSET + Vdp.GG_WIDTH);
                sourceOffset = newX + (newY * Vdp.SMS_HEIGHT);      
            }
            else
            {
                sourceOffset = Vdp.GG_X_OFFSET + (Vdp.GG_Y_OFFSET * Vdp.SMS_WIDTH);
            }   
        }      
        // ------------------------------------------------------------------------------------
        // SMS: Just render full window as normal regardless
        // ------------------------------------------------------------------------------------
        else if (is_sms)
        {
            renderOffset = Math.max(0, (scaledWidth - screen_width) >> 1);
            
            // Setup width and height of region to render
            renderWidth = Math.min(scaledWidth, screen_width);
            renderHeight = Math.min(scaledHeight, screen_height); 
            
            // Setup source width and height of region to render
            sourceWidth = Vdp.SMS_WIDTH;
            sourceHeight = Vdp.SMS_HEIGHT;
            sourceOffset = 0;
        }
               
        // Co-ordinates to render final image
        renderX = Math.max(0, (screen_width - renderWidth) >> 1);
        renderY = Math.max(0, (screen_height - renderHeight) >> 1);
    }

    /**
     * 
     * Fixed point image scaling code (16.16)
     * 
     * Speed increases when scaling smaller images. Scaling images up is expensive.
     * 
     * @param src          pointer to the image we want to scale
     * @param srcwid       how wide is the entire source image?
     * @param srchgt       how tall is the entire source image?
     * @param src_pointer  offset into source image
     * @param src_areaw    source area width
     * @param src_areah    source area height
     * @param dest         pointer to the bitmap we want to scale into (destination)
     * @param dstwid       how wide do we want the source image to be?
     * @param dsthgt       how tall do we want the source image to be?
     *            
     * Note that both srcwid&srchgt and dstwid&dsthgt refer to the source image's dimensions. 
     * The destination page size is specified in pagewid&pagehgt.
     * 
     * @author Chris White
     * 
     */

    private final void scale( int[] src, int srcwid, int srchgt, 
                              int src_pointer, int src_areaw, int src_areah,
                              int[] dest, int dstwid, int dsthgt)
    {
        int xstep = (src_areaw << 16) / dstwid; // calculate distance (in source) between
        int ystep = (src_areah << 16) / dsthgt; // pixels (in dest)

        int dest_pointer = 0; // set our pointer to the first pixel

        int srcy = 0; // y-cordinate in source image
        
        for (int y = 0; y < dsthgt; y++)
        {
            int srcx = 0; // reset our x counter before each row...

            for (int x = 0; x < dstwid; x++)
            {
                dest[dest_pointer++] = src[(srcx >> 16) + src_pointer]; // copy next pixel
                srcx += xstep; // move through source image
            }
            
            //
            // figure out if we are still on the same row as last time
            // through the loop, and if so we move the source pointer accordingly.
            // If not, we add nothing to the source counter, and go back to the beginning
            // of the row (in the source image) we just drew.
            //

            srcy += ystep; // move through the source image...
            src_pointer += (srcy >> 16) * srcwid; // and possibly to the next row.
            srcy &= 0xffff; // set up the y-coordinate between 0 and 1
        }
    }
    
    // -------------------------------------------------------------------------------------------- //
    // Image Rotation Code
    //--------------------------------------------------------------------------------------------- //
    
    /** Rotation (0, 90, 180, 270) */
    public static int rotate; 
    
    private int[] rotatedDisplay;

    
    /* --------------------------------------------------------------------------------------------
     * Rotate 90 Degrees Clockwise
     *                         ___________
     *                         | ----- > |
     *                         |         |
     * ________________        |         |
     * |              |  90    |         |
     * | ^            |  CW    |         |
     * | |            | =====> |         |
     * | |            |        |         |
     * |______________|        |_________|
     --------------------------------------------------------------------------------------------*/
    
    private final void rotate90(int[] original)
    {
        int destIndex = 0;
        int baseIndex = (Vdp.SMS_WIDTH * (Vdp.SMS_HEIGHT - 1));
        
        for (int x = 0; x < Vdp.SMS_WIDTH; x++)
        {
            int sourceIndex = x + baseIndex;
     
            for (int y = Vdp.SMS_HEIGHT; y-- != 0;)
            {
                rotatedDisplay[destIndex++] = original[sourceIndex];
                sourceIndex -= Vdp.SMS_WIDTH; // goto previous row
            }
        }       
    }
    
    /* --------------------------------------------------------------------------------------------
     * Rotate 270 Degrees Clockwise
     *                         ___________
     *                         |         |
     *                         |         |
     * ________________        |         |
     * |              |  270   |         |
     * | ^            |  CW    |         |
     * | |            | =====> |         |
     * | |            |        | < ----  |
     * |______________|        |_________|
     --------------------------------------------------------------------------------------------*/
    
    private final void rotate270(int[] original)
    {
        int destIndex = rotatedDisplay.length;
        int baseIndex = (Vdp.SMS_WIDTH * (Vdp.SMS_HEIGHT - 1));
        
        for (int x = 0; x < Vdp.SMS_WIDTH; x++)
        {
            int sourceIndex = x + baseIndex;
     
            for (int y = Vdp.SMS_HEIGHT; y-- != 0;)
            {
                rotatedDisplay[--destIndex] = original[sourceIndex];
                sourceIndex -= Vdp.SMS_WIDTH; // goto previous row
            }
        }       
    }
    
    /* --------------------------------------------------------------------------------------------
     * Rotate 180 Degrees Clockwise
     * ________________        ________________    
     * |              |  180   |              |
     * | *            |  CW    |            | |
     * | |            | =====> |            | |
     * | |            |        |            * |
     * |______________|        |______________|
     --------------------------------------------------------------------------------------------*/
        
    private final void rotate180(int[] original)
    {
        int destIndex = 0;
        int baseIndex = (Vdp.SMS_WIDTH * (Vdp.SMS_HEIGHT - 1));
        
        for (int y = Vdp.SMS_HEIGHT; y-- != 0;)
        { 
            for (int x = Vdp.SMS_WIDTH; x-- != 0;)
            {
                rotatedDisplay[destIndex++] = original[x + baseIndex];
            }
            
            baseIndex -= Vdp.SMS_WIDTH;
        }
    }
}