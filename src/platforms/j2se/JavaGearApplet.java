import javax.swing.*;
import java.awt.event.*;

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

public class JavaGearApplet extends JApplet
    implements ComponentListener
{    
    private Engine engine; 
    
    public static JavaGearApplet applet;
    /* 
     * Called by the browser or applet viewer to inform this applet that it has been 
     * loaded into the system.
     */
    
    public void init()
    {
        applet = this;
        
        addComponentListener(this);
        
        // Read parameters from html
        String rom = getParameter("rom");        
        String width = getParameter("width");
        String height = getParameter("height");
        String video = getParameter("video");
        
        
        // Rom has been specified
        if (rom != null && !rom.equals(""))
        {
            Engine.useRomFile = rom;
        }
        
        // Video mode has been specified
        if (video != null)
        {
            video = video.toLowerCase();           
            Vdp.videoMode = video.equals("pal") ? Vdp.PAL : Vdp.NTSC;
        }
        
        if (width != null && height != null)
        {
            try
            {
                int w = Integer.parseInt(width);
                int h = Integer.parseInt(height);
                
                Platform.screen_width = w;
                Platform.screen_height = h;
            }
            catch (NumberFormatException e)
            {
                Platform.screen_width = Vdp.SMS_WIDTH * 2;
                Platform.screen_height = Vdp.SMS_HEIGHT * 2;
            }
        }
        else
        {
            Platform.screen_width = Vdp.SMS_WIDTH * 2;
            Platform.screen_height = Vdp.SMS_HEIGHT * 2;           
        }

        Platform.mode = Platform.MODE_APPLET;

        engine = new Engine(null);
        engine.start();   
               
    }
    
    /*
     * Called after init() and each time the applet is revisited in a Web page. 
     */
    public void start()
    {   
        setFocusable(true);
        requestFocus();
        requestFocusInWindow();
    }
    
    public void stop()
    {
        if (engine != null)
            engine.initMenu();
    }
    
    public void destroy()
    {
        Engine.running = false;
        Engine.engine = null;
        engine = null;
        applet = null;
    }
    
    // --------------------------------------------------------------------------------------------
    // ComponentListener
    // --------------------------------------------------------------------------------------------

    public void componentResized(ComponentEvent e)
    {
    }

    public void componentMoved(ComponentEvent e)
    {
    }

    public void componentHidden(ComponentEvent e)
    {
    }

    public void componentShown(ComponentEvent e)
    {
        removeComponentListener(this);
        requestFocus();
    }
}