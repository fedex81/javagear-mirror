import javax.swing.*;
import javax.swing.filechooser.FileFilter;

import java.awt.event.*;
import java.io.*; 


/**
 * J2SE Application: Full User Interface
 * 
 * Note the applet / midp / lite versions do not use this.
 */

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

public class UIMenuBar
    implements ActionListener
{
    // --------------------------------------------------------------------------------------------
    // Menu Strings
    // --------------------------------------------------------------------------------------------
    
    private final static String ABOUT_TEXT = 
        Setup.PROGRAM_NAME + " " + BuildSettings.VERSION + "\n" +
        "Copyright (c) 2002-2008 Chris White\n" +
        "All rights reserved.\n\n" +
        "www.javagear.co.uk\n\n" +
        "JavaGear is strictly a non-profit project. JavaGear is free.\nIts source code is free. Selling it is not allowed.\n\n" +
        "Special thanks to Charles MacDonald, Maxim and everyone at smspower.org";
   
    private final static String 
        ERROR_LOAD_STATE = "Error loading state ",
        ERROR_SAVE_STATE = "Error saving state ";
    
    private final static String
        FILE_DESCRIPTION = "Sega Master System & GameGear Roms";
    
    private final static String
        FILE = "File",
        FILE_INSERT = "Insert Cartridge",
        FILE_REMOVE = "Remove Cartridge",
        FILE_LOAD_STATE = "Load State",
        FILE_SAVE_STATE = "Save State",
        FILE_SLOT = "Select Slot",
        FILE_SLOT_0 = "0",
        FILE_SLOT_1 = "1",
        FILE_SLOT_2 = "2",
        FILE_SLOT_3 = "3",
        FILE_EXIT = "Exit",
        
        SYSTEM = "System",
        SYSTEM_REGION = "Region",
        SYSTEM_REGION_US = "USA / Europe",
        SYSTEM_REGION_JAP = "Japan",
        SYSTEM_TV_TYPE = "TV Type",
        SYSTEM_TV_NTSC = "NTSC",
        SYSTEM_TV_PAL = "PAL",
        SYSTEM_HARD_RESET = "Hard Reset",
        
        SETTINGS = "Settings",
        SETTINGS_SOUND = "Enable Sound",
        SETTINGS_CONTROLS = "Controls",
        SETTINGS_CONTROLS_DEFINE = "Define Keys",
        SETTINGS_CONTROLS_LIGHTGUN = "Enable Lightgun",
        SETTINGS_WINDOW_SIZE = "Window Size",
        SETTINGS_WINDOW_X1 = "x1",
        SETTINGS_WINDOW_X2 = "x2",
        SETTINGS_WINDOW_X3 = "x3",
        SETTINGS_WINDOW_X4 = "x4",
        SETTINGS_FRAMESKIP = "Frameskip",
        SETTINGS_FRAMESKIP_AUTO = "Auto",
        SETTINGS_FRAMESKIP_OFF = "Disabled",
        SETTINGS_THROTTLE = "Enable Throttle",
        
        HELP = "Help",
        HELP_ABOUT = "About";
    
    // --------------------------------------------------------------------------------------------
    // Menu Settings
    // --------------------------------------------------------------------------------------------
        
    /** Checkbox for lightgun */
    public JCheckBoxMenuItem checkLightGun;
    
    private final static String FILE_SETTINGS = "javagear_full.settings";
    
    /** Directory to load files from */
    private static String directory = ".";
    
    /** Default window size */
    public static int windowSize = JavaGear.DEFAULT_ZOOM;
        
    
    // --------------------------------------------------------------------------------------------
    
    public UIMenuBar(JFrame jframe)
    {        
        // Ensure menus display over the emulation pane
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        
        // Create Menu Bar
        JMenuBar menuBar = new JMenuBar();
        
        // ----------------------------------------------------------------------------------------
        // FILE MENU
        // ----------------------------------------------------------------------------------------
        
        menuBar.add(makeMenu(FILE,
            new Object[]
            {
                FILE_INSERT,
                FILE_REMOVE,
                null,
                FILE_LOAD_STATE,
                FILE_SAVE_STATE,
                makeMenu(FILE_SLOT,
                    makeGroup(new String[] {FILE_SLOT_0,FILE_SLOT_1,FILE_SLOT_2,FILE_SLOT_3}, 0), this),
                null,
                FILE_EXIT
            }, this));
        
        // ----------------------------------------------------------------------------------------
        // SYSTEM MENU
        // ----------------------------------------------------------------------------------------
        
        int usOption = Ports.isDomestic() ? 0 : 1;
        int tvOption = Vdp.videoMode == Vdp.NTSC ? 0 : 1;

        menuBar.add(makeMenu(SYSTEM,
                new Object[]
                {
                    makeMenu(SYSTEM_REGION,
                        makeGroup(new String[] {SYSTEM_REGION_US , SYSTEM_REGION_JAP}, usOption), this),
                    makeMenu(SYSTEM_TV_TYPE,
                        makeGroup(new String[] {SYSTEM_TV_NTSC , SYSTEM_TV_PAL}, tvOption), this),
                    null,
                    SYSTEM_HARD_RESET,
                }, this));
  
        // ----------------------------------------------------------------------------------------
        // SETTINGS MENU
        // ----------------------------------------------------------------------------------------
        
        int fsOption = Throttle.autoFS ? 0 : 1;
        
        checkLightGun = new JCheckBoxMenuItem(SETTINGS_CONTROLS_LIGHTGUN, Engine.lightgunEnabled);

        menuBar.add(makeMenu(SETTINGS,
                new Object[]
                {
                    new JCheckBoxMenuItem(SETTINGS_SOUND, Platform.SUPPORTS_SOUND && Engine.soundEnabled),
                    null,
                    makeMenu(SETTINGS_CONTROLS,
                            new Object[] {SETTINGS_CONTROLS_DEFINE , checkLightGun}, this),                    
                    null,
                    makeMenu(SETTINGS_WINDOW_SIZE,
                        makeGroup(new String[] {SETTINGS_WINDOW_X1 , SETTINGS_WINDOW_X2, SETTINGS_WINDOW_X3, SETTINGS_WINDOW_X4}, windowSize-1), this),
                    makeMenu(SETTINGS_FRAMESKIP,
                        makeGroup(new String[] {SETTINGS_FRAMESKIP_AUTO , SETTINGS_FRAMESKIP_OFF}, fsOption), this),
                    null,
                    new JCheckBoxMenuItem(SETTINGS_THROTTLE, Throttle.isEnabled())
                }, this));
        
        // ----------------------------------------------------------------------------------------
        // HELP MENU
        // ----------------------------------------------------------------------------------------
        
        menuBar.add(makeMenu(HELP,
                new Object[]
                {
                    HELP_ABOUT,
                }, this));
        
        jframe.setJMenuBar(menuBar);
    }
    
    public static void loadMenuOptions()
    {
        try
        {     
            byte[] data = Platform.load(FILE_SETTINGS);
            
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            DataInputStream inData = new DataInputStream(in); 
             
            // System
            Vdp.videoMode = inData.readInt();           
            Ports.setDomestic(inData.readBoolean());
            
            // Settings
            Engine.soundEnabled = inData.readBoolean();
            windowSize = inData.readInt();
            Throttle.autoFS = inData.readBoolean();
            Throttle.throttle = inData.readBoolean();
            Engine.lightgunEnabled = inData.readBoolean();
            
            // Keys
            Platform.K_UP_MAP = inData.readInt();
            Platform.K_DOWN_MAP = inData.readInt();
            Platform.K_LEFT_MAP = inData.readInt();
            Platform.K_RIGHT_MAP = inData.readInt();          
            Platform.K_FIRE1_MAP = inData.readInt();
            Platform.K_FIRE2_MAP = inData.readInt();
            Platform.K_START_MAP = inData.readInt();
            
            // Other settings
            directory = inData.readUTF();

            inData.close();
            in.close();
        }
        catch (Exception e)
        {
            //if (DEBUG)
                //e.printStackTrace();
        }   
    }
    
    public static void saveMenuOptions()
    {
        try
        {    
            ByteArrayOutputStream out = new ByteArrayOutputStream(); 
            DataOutputStream outData = new DataOutputStream(out);  

            // System
            outData.writeInt(Vdp.videoMode);           
            outData.writeBoolean(Ports.isDomestic());
            
            // Settings
            outData.writeBoolean(Engine.soundEnabled);
            outData.writeInt(windowSize);
            outData.writeBoolean(Throttle.autoFS);
            outData.writeBoolean(Throttle.throttle);
            outData.writeBoolean(Engine.lightgunEnabled);
            
            // Keys
            outData.writeInt(Platform.K_UP_MAP);
            outData.writeInt(Platform.K_DOWN_MAP);
            outData.writeInt(Platform.K_LEFT_MAP);
            outData.writeInt(Platform.K_RIGHT_MAP);        
            outData.writeInt(Platform.K_FIRE1_MAP);
            outData.writeInt(Platform.K_FIRE2_MAP);
            outData.writeInt(Platform.K_START_MAP);
            
            // Other settings
            outData.writeUTF(directory);
                      
            byte[] data = out.toByteArray();                   
            
            outData.close();
            out.close();   
            
            Platform.save(FILE_SETTINGS, data);
        }
        catch (Exception e)
        {
            //if (DEBUG)
            //    e.printStackTrace();
        }        
    }
    
    /**
     *  Invoked when a menu item is selected
     */

    public void actionPerformed(ActionEvent evt)
    {
        Engine engine = Engine.engine;
        String arg = evt.getActionCommand();
        
        // ----------------------------------------------------------------------------------------
        // Main Menu Actions
        // ----------------------------------------------------------------------------------------
        
        // Insert cartridge
        if (arg == FILE_INSERT)
        {
            pauseEmulator();
            engine.saveSRAM();
            engine.jframe.repaint(); // paint black
            
            File rom = openDialog();
            
            if (rom != null)
            {
                Platform.isCartLoaded = true;
                engine.initRom(rom.getAbsolutePath());
                engine.resizeWindow(windowSize); // resize for game gear or sms display
                
                // Stop lightgun by default
                engine.platformFunction(engine, Engine.PLATFORM_STOP_LIGHTGUN);
                engine.start();
            }
            else
            // Resume previous rom
                resumeEmulator();
        }
        else if (arg == FILE_REMOVE)
        {   
            if (Platform.isCartLoaded)
            {
                pauseEmulator();
                engine.saveSRAM();
                Platform.isCartLoaded = false;
                engine.jframe.repaint(); // paint black
            }      
        }
        else if (arg == FILE_LOAD_STATE)
        {
            if (Platform.isCartLoaded)
            {
                pauseEmulator();
                
                if (!engine.loadState())
                {
                   engine.jframe.repaint(); // paint black
                   JOptionPane.showMessageDialog(engine.jframe, ERROR_LOAD_STATE+engine.slot, FILE_LOAD_STATE, JOptionPane.PLAIN_MESSAGE);
                }
                
                resumeEmulator();
            }
        }
        else if (arg == FILE_SAVE_STATE)
        {
            if (Platform.isCartLoaded)
            {
                pauseEmulator();
                              
                if (!engine.saveState())
                {
                    engine.jframe.repaint(); // paint black
                    JOptionPane.showMessageDialog(engine.jframe, ERROR_SAVE_STATE+engine.slot, FILE_SAVE_STATE, JOptionPane.PLAIN_MESSAGE);
                }
                
                resumeEmulator();
            }
        }
        else if (arg == FILE_SLOT_0)
            engine.slot = 0;
        else if (arg == FILE_SLOT_1)
            engine.slot = 1;
        else if (arg == FILE_SLOT_2)
            engine.slot = 2;
        else if (arg == FILE_SLOT_3)
            engine.slot = 3;
        
        // Exit JavaGear
        else if (arg == FILE_EXIT)
        {
            engine.exit();
        }
        
        // ----------------------------------------------------------------------------------------
        // System Menu Actions
        // ----------------------------------------------------------------------------------------
        else if (arg == SYSTEM_REGION_US)
        {
            Ports.setDomestic(true);
        }
        else if (arg == SYSTEM_REGION_JAP)
        {
            Ports.setDomestic(false);
        }
        else if (arg == SYSTEM_TV_NTSC)
        {
            if (Platform.isCartLoaded) pauseEmulator();
            engine.setVideoTiming(Vdp.NTSC);
            resumeEmulator();
        }
        else if (arg == SYSTEM_TV_PAL)
        {
            if (Platform.isCartLoaded) pauseEmulator();
            engine.setVideoTiming(Vdp.PAL);
            resumeEmulator();
        } 
        else if (arg == SYSTEM_HARD_RESET)
        {
            if (Platform.isCartLoaded) pauseEmulator();
            engine.saveSRAM();
            engine.reset();
            resumeEmulator();
        }
        
        // ----------------------------------------------------------------------------------------
        // Settings Menu Actions
        // ----------------------------------------------------------------------------------------
        else if (arg == SETTINGS_CONTROLS_DEFINE)
        {
            UIDefineKeys define = new UIDefineKeys();           
        }
        else if (arg == SETTINGS_CONTROLS_LIGHTGUN)
        {
            if (checkLightGun.isSelected())
            {
                engine.platformFunction(engine, Engine.PLATFORM_INIT_LIGHTGUN);            
            }
            else
            {
                engine.platformFunction(engine, Engine.PLATFORM_STOP_LIGHTGUN);
            }
        }
        else if (arg == SETTINGS_WINDOW_X1)
        {
            if (windowSize != 1)
            {
                if (Platform.isCartLoaded) pauseEmulator();
                engine.resizeWindow(1);
                windowSize = 1;
                resumeEmulator();
            }
        }
        else if (arg == SETTINGS_WINDOW_X2)
        {
            if (windowSize != 2)
            {           
                if (Platform.isCartLoaded) pauseEmulator();
                engine.resizeWindow(2);
                windowSize = 2;
                resumeEmulator();
            }
        }
        else if (arg == SETTINGS_WINDOW_X3)
        {
            if (windowSize != 3)
            {
                if (Platform.isCartLoaded) pauseEmulator();
                engine.resizeWindow(3);
                windowSize = 3;  
                resumeEmulator();
            }
        }
        else if (arg == SETTINGS_WINDOW_X4)
        {
            if (windowSize != 4)
            {
                if (Platform.isCartLoaded) pauseEmulator();
                engine.resizeWindow(4);
                windowSize = 4;
                resumeEmulator();
            }
        }
        else if (arg == SETTINGS_SOUND)
        {
            if (Platform.isCartLoaded) pauseEmulator();
            
            Engine.soundEnabled = !Engine.soundEnabled;
            
            resumeEmulator();
        }
        else if (arg == SETTINGS_FRAMESKIP_AUTO)
        {
            Throttle.enableAutoFrameSkip(true);
        }
        else if (arg == SETTINGS_FRAMESKIP_OFF)
        {
            Throttle.enableAutoFrameSkip(false);
        }
        else if (arg == SETTINGS_THROTTLE)
        {
            //if (Platform.isCartLoaded) pauseEmulator();
            
            Throttle.throttle = !Throttle.throttle;
            
            if (Throttle.throttle)
                engine.platformFunction(engine, Engine.PLATFORM_THROTTLE_INIT);
            
            //resumeEmulator();
        }
        // ----------------------------------------------------------------------------------------
        // Help Menu Actions
        // ----------------------------------------------------------------------------------------
        else if (arg == HELP_ABOUT)
        {
            JOptionPane.showMessageDialog(engine.jframe, ABOUT_TEXT, HELP_ABOUT, JOptionPane.PLAIN_MESSAGE);
        }        
    }
    
    private void pauseEmulator()
    {
        Engine.engine.stop(true);
    }
    
    private void resumeEmulator()
    {
        if (Engine.soundEnabled)
            Engine.engine.platformFunction(Engine.engine, Engine.PLATFORM_AUDIO_START);
         
        if (Platform.isCartLoaded) Engine.engine.start();           
    }
    
    // --------------------------------------------------------------------------------------------
    // UI Building Code Follows
    // --------------------------------------------------------------------------------------------
    
    /**
        Building Menu Method.
        From: Core Java Volume 1 (1999 Sun Microsystems) P 489
     */

    private static JMenu makeMenu(Object parent, Object[] items, Object target)
    {
        JMenu m = null;
        if (parent instanceof JMenu)
            m = (JMenu)parent;
        else if (parent instanceof String)
            m = new JMenu((String)parent);
        else
            return null;
    
        for (int i=0; i < items.length; i++)
        {
            if (items[i] == null)
                m.addSeparator();
            else
                m.add(makeMenuItem(items[i], target));
        }

        return m;
    }
    
    /**
        Building Menu Method.
        From: Core Java Volume 1 (1999 Sun Microsystems) P 489
    */
    
    private static JMenuItem makeMenuItem(Object item, Object target)
    {
        JMenuItem r = null;
        if (item instanceof String)
            r = new JMenuItem((String)item);
        else if (item instanceof JMenuItem)
            r = (JMenuItem)item;
        else return null;
    
        if (target instanceof ActionListener)
            r.addActionListener((ActionListener)target);
        return r;
    }
    
    private JRadioButtonMenuItem[] makeGroup(String[] s, int defaultOption)
    {
        JRadioButtonMenuItem[] buttons = new JRadioButtonMenuItem[s.length];
        
        ButtonGroup bg = new ButtonGroup();
        
        for (int i = 0; i < s.length; i++)
        {
            buttons[i] = new JRadioButtonMenuItem(s[i]);
            bg.add(buttons[i]);
        }
        
        // Set default choice
        buttons[defaultOption].setSelected(true);
        
        return buttons;     
    }
    
    /**
     *  Invoke Dialog to select cartridge image.
     *
     *  @return     URL of chosen cartridge
     */

    private File openDialog()
    {
        File filename = null;

        // Create Frame for File Chooser
        JFrame openFrame = new JFrame(FILE_INSERT);

        // Create new File Chooser
        JFileChooser chooser = new JFileChooser();

        // Set Directory
        chooser.setCurrentDirectory(new File(directory));

        // Set FileFilters   
        chooser.addChoosableFileFilter(new SimpleFileFilter(new String[] {"sms", "gg", "zip"}, FILE_DESCRIPTION));
        chooser.setAcceptAllFileFilterUsed(false); // turn off *.* filter

        // This call does not return until a file is selected
        int result = chooser.showOpenDialog(openFrame);

        // Get the Filename
        File selectedFile = chooser.getSelectedFile();

        if (selectedFile != null && result == JFileChooser.APPROVE_OPTION)
        {
            //System.out.println("Chosen file is " + selectedFile.getPath());
            directory = selectedFile.getAbsolutePath();
            return selectedFile;
        }
        return null;
    }
    
    class SimpleFileFilter extends FileFilter
    {
        String[] extensions;
        String description;

        public SimpleFileFilter(String[] exts, String descr)
        {
            // Clone and lowercase the extensions
            extensions = new String[exts.length];
            for (int i = exts.length - 1; i >= 0; i--)
                extensions[i] = exts[i].toLowerCase();

            // Make sure we have a valid (if simplistic) description
            description = (descr == null ? exts[0] + " files" : descr);
        }

        public boolean accept(File f)
        {
            // We always allow directories, regardless of their extension
            if (f.isDirectory())
                return true;

            // Ok, it's a regular file, so check the extension
            String name = f.getName().toLowerCase();
            for (int i = extensions.length; i-- != 0;)
            {
                if (name.endsWith(extensions[i]))
                    return true;
            }
            return false;
        }

        public String getDescription()
        {
            return description;
        }
    }
}