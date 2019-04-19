import javax.microedition.lcdui.*;

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

public class PlatformImg
{   
    /** Image data */ 
    public Image image;
    
    /** Top left clipping co-ordinates */
    private int clip_x, clip_y;
    
    /** Image Width, Height */
    public short width, height;
   
    public PlatformImg(byte [] imagedata)
    {
        try
        {
            image = Image.createImage(imagedata, 0, imagedata.length);
            width = (short)image.getWidth();
            height = (short)image.getHeight();
        }
        catch(Exception e)
        {
            if (Platform.DEBUG)
            {
                e.printStackTrace();
            }
        }
    }
    
    public PlatformImg(PlatformImg tileImage, int x, int y, int w, int h)
    {
        width = (short) w;
        height = (short) h;

        image = tileImage.image;
    
        clip_x = x;
        clip_y = y;
    }

    public void drawImage(Gfx gfx, int x, int y)
    {
        gfx.g.drawRegion(image, clip_x, clip_y, width, height, 0, x, y, Graphics.TOP | Graphics.LEFT);
    }
}