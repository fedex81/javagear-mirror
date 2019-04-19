import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

public class Platform
    implements KeyListener
{   
    /** Debug output (console) */
    public final static boolean DEBUG = true;
    
    /** J2SE Version */
    public final static int ID = Engine.J2SE;
 
    // --------------------------------------------------------------------------------------------
    // Default Emulator Settings
    // --------------------------------------------------------------------------------------------
    
    /** Default frameskip value */
    public final static int DEFAULT_FRAMESKIP = 0;
    
    /** Turn throttling on by default? */
    public final static boolean DEFAULT_THROTTLE = true;
    
    /** Default minimum sleep value */
    public final static int DEFAULT_MIN_SLEEP = 3;
    
    /** Turn scale on by default? */
    public final static boolean DEFAULT_SCALE = true;
    
    /** Call Thread.yield() per rendered frame */
    public final static boolean THREAD_YIELD = false;
    
    /** Thread Priority */
    public static int THREAD_PRIORITY = Thread.NORM_PRIORITY;
    
    /** J2SE Mode: Applet, Application or Lite version */
    public static int mode;
    
    public static int
        MODE_APPLET = 0,    // Applet
        MODE_FULL = 1,      // Full Application
        MODE_LITE = 2;      // Lite Application (Similar to Applet/J2ME version)
    
    /** Is cartridge inserted? */
    public static boolean isCartLoaded= false;

    
    // --------------------------------------------------------------------------------------------
    // Default Key Mappings
    // --------------------------------------------------------------------------------------------
    
    public static int
        K_MENU_UP_MAP = KeyEvent.VK_UP,
        K_MENU_DOWN_MAP = KeyEvent.VK_DOWN,
        K_MENU_SELECT_MAP = KeyEvent.VK_Z,
    
        K_UP_MAP = KeyEvent.VK_UP,
        K_DOWN_MAP = KeyEvent.VK_DOWN,
        K_LEFT_MAP = KeyEvent.VK_LEFT,
        K_RIGHT_MAP = KeyEvent.VK_RIGHT,
    
        K_UL_MAP = KeyEvent.VK_7,
        K_UR_MAP = KeyEvent.VK_9,
        K_DL_MAP = KeyEvent.VK_1,
        K_DR_MAP = KeyEvent.VK_3,
        
        K_FIRE1_MAP = KeyEvent.VK_Z,
        K_FIRE2_MAP = KeyEvent.VK_X,
        K_START_MAP = KeyEvent.VK_ENTER,
        
        K_MENU_MAP = KeyEvent.VK_0;
    
    // --------------------------------------------------------------------------------------------
    // Sound Output
    // --------------------------------------------------------------------------------------------
    
    /** Does platform support sound? */
    public final static boolean SUPPORTS_SOUND = true;
    
    /** Sample Rate */
    public final static int SAMPLE_RATE = 22050;
    
    /** Sound class (Java 2 and above) */
    private JavaxSound sound;
    
    // --------------------------------------------------------------------------------------------
    // Panels and rendering
    // --------------------------------------------------------------------------------------------
    
    /** Window width & height */
    public static int screen_width, screen_height;

    public ScreenPanel sp;
    private UIPanel ui;
    
    private Component currentPanel;
    
    public JFrame jframe;
    
    /** Menu bar on J2SE application */
    private UIMenuBar menubar;
    
    // --------------------------------------------------------------------------------------------
    
    
    public Platform()
    {        
        sp = new ScreenPanel();
        sp.setSize(new Dimension(screen_width,screen_height));
        
        ui = new UIPanel();
        ui.setSize(new Dimension(screen_width, screen_height));
        
        // Only initialise sound class if it exists in this java version
        if (SUPPORTS_SOUND)
        {
            try
            {
                Class.forName ("javax.sound.sampled.DataLine");
                sound = new JavaxSound();
            }
            catch (Throwable t)
            {
                sound = null;
            }
        }
 
        if (mode != MODE_APPLET)
        {
            // Set System Look and Feel if possible
            try
            {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
            catch (Exception e){}
            
            // Applet gets max priority
            //THREAD_PRIORITY = Thread.MAX_PRIORITY;
            
            jframe = new JFrame();
            
            // Setup Frame
            jframe.setTitle(Setup.PROGRAM_NAME);
            
            // Do Icon
            URL icon = getURL("i.png");
//            jframe.setIconImage(Toolkit.getDefaultToolkit().getImage(icon));
            
            // Set Menu Bar
            if (mode == MODE_FULL)
            {
                menubar = new UIMenuBar(jframe);
                K_MENU_MAP = -1; // Disable menu key
            }
            else
            {
                platformFunction(null, Engine.PLATFORM_INIT_LIGHTGUN);
                isCartLoaded = true;   
            }
    
            // Get Current Screen Size
            Toolkit tk = Toolkit.getDefaultToolkit();
            Dimension d = tk.getScreenSize();
            // Set Window in middle
            jframe.setLocation(d.width / 4, d.height / 4);
    
            // Add Window Listener
            jframe.addWindowListener(new WindowAdapter()
            {
                public void windowClosing(WindowEvent e) {exit();}
             });
            
            // Add Keyboard Support
            jframe.addKeyListener(this);

            jframe.setVisible(true);
        }
        else
        {    
            platformFunction(null, Engine.PLATFORM_INIT_LIGHTGUN);
            JavaGearApplet.applet.addKeyListener(this);
            JavaGearApplet.applet.setVisible(true);
        }
    }
    
    public void exit()
    {           
        if (sound != null)
            sound.audioStop();
        
        if (mode != MODE_APPLET)
        {            
            if (Engine.engine != null) 
                Engine.engine.stop(mode == MODE_FULL);
            
            if (mode == MODE_FULL)
                UIMenuBar.saveMenuOptions();
            else
                Engine.engine.saveSettings();
            
            Engine.engine.saveSRAM();
            
            jframe.setVisible(false);
            jframe.dispose(); // Remove JFrame
            System.exit(0);
        }
    }
    
    public void resizeWindow(int size)
    {
        if (Engine.emuWidth == 0)
            Engine.emuWidth = Vdp.SMS_WIDTH;
        if (Engine.emuHeight == 0)
            Engine.emuHeight = Vdp.SMS_HEIGHT;
        
        screen_width = Engine.emuWidth * size;
        screen_height = Engine.emuHeight * size;
        
        sp.changeSize(screen_width, screen_height);
        ui.setSize(new Dimension(screen_width, screen_height));
        
        Engine.engine.setupScreen(true); 
        
        setPanel(sp);
    }
    
    public void initJ2SEApplicationVersion(Engine e)
    {
        e.vdp = new Vdp(Engine.display);
        if (SUPPORTS_SOUND)
            e.psg = new SN76489();
        e.ports = new Ports(e.vdp, e.psg);
        e.z80 = new Z80(e.ports);            
    }
    
    // --------------------------------------------------------------------------------------------
    // Platform Specific Calls
    // --------------------------------------------------------------------------------------------
    
    public void platformFunction(Engine e, int function)
    {
        switch (function)
        {
            case Engine.PLATFORM_THROTTLE_INIT:
                Throttle.init(e.fps);
                break;
            
            case Engine.PLATFORM_THROTTLE:
                Throttle.throttle();
                break;
            
            case Engine.PLATFORM_FRAMESKIP:
                Throttle.setFrameSkip(Engine.frameSkip);
                break;
                
            case Engine.PLATFORM_AUDIO_START:
                if (sound != null) sound.audioStart();
                break;
                
            case Engine.PLATFORM_AUDIO_STOP:
                if (sound != null) sound.audioStop();
                break; 
                
            case Engine.PLATFORM_INIT_LIGHTGUN:
                sp.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
                Engine.lightgunEnabled = true;
                if (menubar != null)
                    menubar.checkLightGun.setSelected(true);
                break;
                
            case Engine.PLATFORM_STOP_LIGHTGUN:
                sp.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                Engine.lightgunEnabled = false;
                if (menubar != null)
                    menubar.checkLightGun.setSelected(false);
                break;
        }
    }
    
    // --------------------------------------------------------------------------------------------
    // Sound Output
    // --------------------------------------------------------------------------------------------
      
    public void audioOutput(byte[] buffer)
    {
        if (sound != null)
            sound.audioOutput(buffer);
    }

    
    // --------------------------------------------------------------------------------------------
    // Screen Rendering
    // --------------------------------------------------------------------------------------------
    
    public void doRepaint()
    {
        switch (Engine.state)
        {
            case Engine.S_EMULATE:
                if (currentPanel != sp) 
                    setPanel(sp);
                sp.refresh();
                break;
                
            case Engine.S_MENU:
            case Engine.S_CLS:
                if (currentPanel != ui) 
                    setPanel(ui);
                ui.repaint();
                break;
        }
    
    }
    
    private void setPanel(Component newPanel)
    {
        if (mode != MODE_APPLET) {
            // Add Display Window
            if (currentPanel != null)
                jframe.getContentPane().remove(currentPanel);

            currentPanel = newPanel;

            jframe.getContentPane().add(newPanel);
            jframe.setMinimumSize(jframe.getPreferredSize());
            jframe.pack();
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            jframe.setResizable(false);
            jframe.setVisible(true);
            jframe.requestFocus();

        }
        else
        {
            if (currentPanel != null)
                JavaGearApplet.applet.getContentPane().remove(currentPanel);
            
            currentPanel = newPanel;
            
            JavaGearApplet.applet.getContentPane().add(newPanel);
            JavaGearApplet.applet.requestFocus();            
        }
    }
    
    // --------------------------------------------------------------------------------------------
    // Input
    // --------------------------------------------------------------------------------------------
    
    public synchronized void keyPressed(KeyEvent evt)
    {
        Engine.keyCode = evt.getKeyCode();
        Engine.keyPress(Engine.getKeyMap(Engine.keyCode));
    }
    
    public synchronized void keyReleased(KeyEvent evt)
    {
        Engine.keyRelease(Engine.getKeyMap(evt.getKeyCode()));
    }
    
    public void keyTyped(KeyEvent evt)
    {
        
    }
    
    public String getKeyNm(int key)
    {
       String n = java.awt.event.KeyEvent.getKeyText(key);
       if (n != null) n = n.toUpperCase();      
       return n;
    }
    
    // --------------------------------------------------------------------------------------------
    // File Loading Routines
    // --------------------------------------------------------------------------------------------
    
    public BufferedInputStream getResourceAsStream(String s)
    {
        try
        {
            URL file = getURL(s);
            return new BufferedInputStream(file.openStream());
        }
        catch (Exception e)
        {
            return null;
        }
    } 
    
    /**
     * Get resource that is INSIDE the JAR file
     * 
     * @param s     Resource to grab
     * @return
     */
    
    public static URL getURL(String s)
    {
        return mode == MODE_APPLET ? JavaGearApplet.class.getResource(s) : JavaGear.class.getResource(s);
    }
    
    /**
     * Get resource that is OUTSIDE the JAR file
     * 
     * @param s     File to grab
     * @return
     */
    
    public BufferedInputStream getResourceOutsideJar(String s)
    {
        try
        {
            URL file = mode == MODE_APPLET ?  
                    new URL(JavaGearApplet.applet.getCodeBase(), s) :
                    new File(s).toURI().toURL();                           
                
                return new BufferedInputStream(file.openStream());

        }
        catch (Exception e)
        {
            System.out.println("Failed to get resource: "+s);
            exit();
            return null;
        }        
    }
    
    public byte[][] getZip(Engine engine, String s)
    {
        try
        {
            BufferedInputStream bis = getResourceOutsideJar(s);
            BufferedInputStream file = null;
            
            // Zip file
            if (getExtension(s).equals("zip"))
            {
                ZipInputStream zip = new ZipInputStream(bis);
                
                // Get next entry from the zip file
                ZipEntry entry = zip.getNextEntry();

                // cycle through zip until we find an SMS/GG file
                while (entry != null && file == null)
                {
                    String extension2 = getExtension(entry.getName());
                    
                    // SMS/GG File Found
                    if ((extension2.equals("sms")) || (extension2.equals("gg")))
                    {
                        // Set Emulation Mode
                        if (extension2.equals("sms"))
                            engine.setSMS();
                        else
                            engine.setGG();

                        file = new BufferedInputStream(zip);
                        
                        byte[][] pages = engine.loadROM(file, (int) entry.getSize());
                        zip.close();
                        return pages;
                    }
                    // File is not SMS/GG File
                    else
                    {
                        zip.closeEntry();
                        entry = zip.getNextEntry();
                    }
                }
                zip.close();
                
                // No file found in zip file
                throw new Exception("No rom found in zip file");
            }
        }
        // Zip file kaput
        catch (Exception e)
        {
            return null;
        }
        
        return null;
    }
        
    public int getResourceSize(String s)
    {
        try
        {
            if (mode == MODE_APPLET)
            {
                URL file = new URL(JavaGearApplet.applet.getCodeBase(), s);
                URLConnection conn = file.openConnection();
                return conn.getContentLength();
            }
            else
            {
                return (int) new File(s).length();
            }
        }
        catch (Exception e)
        {
            return -1;
        }          
    }
    
    /**
     *  Strip an extension from a file
     *
     *  @param filename     Filename to strip
     *
     *  @return             Extension of file
     */

    private String getExtension(String filename)
    {
        int i = filename.lastIndexOf('.');
        if (i > 0 && i < filename.length() - 1)
            return filename.substring(i+1).toLowerCase();
        return "";
    }
    
    // --------------------------------------------------------------------------------------------
    // Settings (Not Implemented on Applet Version)
    // --------------------------------------------------------------------------------------------
    
    public static void save(String name, byte[] data)
        throws Exception
    {
        // Do nothing if applet as we don't have access to file system
        if (mode == MODE_APPLET) return;

        new File("settings").mkdir();

        FileOutputStream fos = new FileOutputStream(new File("settings", new File(name).getName()));
        fos.write(data);
        fos.close();        
    }
    
    public static byte[] load(String name)
        throws Exception
    {
        // Do nothing if applet as we don't have access to file system
        if (mode == MODE_APPLET) return null;
        
        File f = new File("settings", new File(name).getName());
        byte[] data = new byte[(int) f.length()];
        
        FileInputStream fis = new FileInputStream(f);
        fis.read(data);
        fis.close();  
        
        return data;
    }  
}