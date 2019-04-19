import java.awt.*;
import java.awt.event.*;
import java.net.URL;

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

public class UIDefineKeys extends Frame
    implements KeyListener, ActionListener
{
    private TextField[] labels;
    
    private Button ok, cancel;
    
    /** Keycodes of defined keys */
    private int[] keyCodes;
    
    // --------------------------------------------------------------------------------------------
    // Strings
    // --------------------------------------------------------------------------------------------
    
    /** Frame title */
    private final static String
        FRAME_TITLE = "Define keys";
    
    /** Key Explanation */
    private final static String labelStrings[] = 
    {
        "Up",
        "Down",
        "Left",
        "Right",
        "Fire A",
        "Fire B",
        "Start/Pause"
    };
    
    private final static String
        BUTTON_OK = "OK",
        BUTTON_CANCEL = "Cancel";
     
    // --------------------------------------------------------------------------------------------
    // Layout setup
    // --------------------------------------------------------------------------------------------
    
    /** Grid layout columns */
    private final static int COLUMNS = 2;
    
    /** Grid layout spacing */
    private final static int SPACING = 3;
    
    
    public UIDefineKeys()
    {
        super();
        
        setLayout(new GridLayout(labelStrings.length + 1, COLUMNS, SPACING, SPACING));
            
        // Setup labels and default keys
        keyCodes = new int[]
        {
                Platform.K_UP_MAP, Platform.K_DOWN_MAP, Platform.K_LEFT_MAP, Platform.K_RIGHT_MAP,
                Platform.K_FIRE1_MAP, Platform.K_FIRE2_MAP, Platform.K_START_MAP
        };
        
        labels = new TextField[labelStrings.length];
      
        for (int i = 0; i < labels.length; i++)
            labels[i] = addTextField(labelStrings[i], keyCodes[i]);
        
        // Setup buttons
        ok = new Button(BUTTON_OK);
        ok.addActionListener(this);
        add(ok);
        
        cancel = new Button(BUTTON_CANCEL);
        cancel.addActionListener(this);
        add(cancel);
        
        // Setup jframe stuff
        setTitle(FRAME_TITLE);
        setResizable(false);
                        
        // Do Icon
        Toolkit tk = Toolkit.getDefaultToolkit();
        URL icon = Platform.getURL("i.png");
//        setIconImage(tk.getImage(icon));
        
        // Add Window Listener
        addWindowListener(new WindowAdapter()
        {
            public void windowClosing(WindowEvent e) {setVisible(false);}
         });
        
        pack();
        
        // Position window
        Dimension d = tk.getScreenSize();
        // Set Window in middle
        setLocation((d.width - getWidth()) / 2, (d.height - getHeight()) / 2);
        
        setVisible(true);
    }
    
    private TextField addTextField(String label, int keyCode)
    {
        TextField tf = new TextField(1);
        tf.addKeyListener(this);
        tf.setText(KeyEvent.getKeyText(keyCode));
        
        add(new Label(label));
        add(tf);
        
        return tf;
    }

    // --------------------------------------------------------------------------------------------
    // Key Listener 
    // --------------------------------------------------------------------------------------------
    
    public void keyPressed(KeyEvent e)
    {
        for (int i = 0; i < labels.length; i++)
        {
            // Clear label when initially pressed
            if (e.getSource() == labels[i])
            {
                labels[i].setText(null);
                return;
            }
        }
    }

    public void keyReleased(KeyEvent e)
    {
        for (int i = 0; i < labels.length; i++)
        {
            // Get selected label
            if (e.getSource() == labels[i])
            {
                keyCodes[i] = e.getKeyCode();
                labels[i].setText(KeyEvent.getKeyText(keyCodes[i]));
                if (i < labels.length - 1)
                    labels[i+1].requestFocus();
                else
                    ok.requestFocus();
                break;
            }
        }
    }

    public void keyTyped(KeyEvent e){}

    // --------------------------------------------------------------------------------------------
    // Action Listener 
    // --------------------------------------------------------------------------------------------
    
    public void actionPerformed(ActionEvent e)
    {
        if (e.getSource() == ok) 
        {
            Platform.K_UP_MAP = keyCodes[0];
            Platform.K_DOWN_MAP = keyCodes[1];
            Platform.K_LEFT_MAP = keyCodes[2];
            Platform.K_RIGHT_MAP = keyCodes[3];           
            Platform.K_FIRE1_MAP = keyCodes[4];
            Platform.K_FIRE2_MAP = keyCodes[5];
            Platform.K_START_MAP = keyCodes[6];
            
            setVisible(false);
        }
        else if (e.getSource() == cancel)
        {
            setVisible(false);
        }
    }
}