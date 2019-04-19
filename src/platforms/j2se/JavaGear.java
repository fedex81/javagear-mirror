
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

import javax.swing.*;

public class JavaGear
{
    /** Default amount to zoom screen */
    public final static int DEFAULT_ZOOM = 2;
        
    public static void main(String[] args)
    {                 
        // Default to full mode
        Platform.mode = Platform.MODE_FULL;
        
        // Read command line arguments
        if (args.length != 0)
        {               
            String midp = getArgumentValue(args, "-midp");
            String width = getArgumentValue(args, "-width");
            String height = getArgumentValue(args, "-height");
            String rom = getArgumentValue(args, "-rom");

            try
            {
                if (midp != null)
                {
                    // Lite Version
                    if (Boolean.parseBoolean(midp))
                    {
                        Platform.mode = Platform.MODE_LITE;
                        
                        if (width != null)
                            Platform.screen_width = Integer.parseInt(width);
                        else
                            Platform.screen_width = Vdp.SMS_WIDTH * DEFAULT_ZOOM;
                        
                        if (height != null)
                            Platform.screen_height = Integer.parseInt(height);
                        else
                            Platform.screen_height = Vdp.SMS_HEIGHT * DEFAULT_ZOOM; 
                    }
                }      
            }
            catch (NumberFormatException e)
            {
                Platform.screen_width = Vdp.SMS_WIDTH * DEFAULT_ZOOM;
                Platform.screen_height = Vdp.SMS_HEIGHT * DEFAULT_ZOOM;
                Platform.mode = Platform.MODE_FULL;
            }

            if (rom != null)
                Engine.useRomFile = rom;
        }
   
        if (Platform.mode != Platform.MODE_FULL)
        {
            Engine engine = new Engine(null);
            engine.start();
        }
        else
        {
                UIMenuBar.loadMenuOptions();
                Engine engine = new Engine(null);
                engine.initJ2SEApplicationVersion(engine);
                engine.resizeWindow(UIMenuBar.windowSize);

                // Autostart if rom specified on command line
                if (Engine.useRomFile != null) {
                    Platform.isCartLoaded = true;
                    engine.initRom(Engine.useRomFile);
                    engine.start();
                }
        }

    }
    
    public static String getArgumentValue(String[] args, String arg)
    {
        for (int i = 0; i < args.length-1; i++)
        {
            String s = args[i];
            
            if (s.equalsIgnoreCase(arg))
                return args[i+1]; 
        }      
        return null;
    }
}