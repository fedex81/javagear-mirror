import java.awt.*;

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

public final class ScreenPanel extends JavaGearPanel
{           
    private Graphics gfx;               // Pointer to Graphics Context  
    private FastImage fast_image;       // Pointer to Fast Image Consumer

    /**
     *  ScreenPanel Constructor.
     */

    public ScreenPanel()
    {
        super();      
        fast_image = new FastImage(Platform.screen_width, Platform.screen_height);  
    }

    public void changeSize(int w, int h)
    {
        gfx = null;
        this.setSize(new Dimension(w, h));
        fast_image = new FastImage(w, h);
    }
    
    /**
     *  Refresh the screen.
     */

    public void refresh()
    {
        // Create Graphics Context for Component
        if (gfx == null) gfx = getGraphics();
        
        fast_image.update(Engine.render, Engine.renderOffset, Engine.renderWidth, Engine.renderHeight, Engine.scaledWidth);

        fast_image.paint(gfx,
                Engine.renderX, Engine.renderY,
                Engine.renderX + Engine.renderWidth, Engine.renderY + Engine.renderHeight,
                0, 0,
                Engine.renderWidth, Engine.renderHeight);
    }


    /**
     *  Repaint Screen When Window Obscured
     * 
     *  @param g    The graphics context to use for painting
     */

    public void paint(Graphics g)
    {
        if (!Platform.isCartLoaded || !Engine.running)
        {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, this.getWidth(), this.getHeight());
        }
    }


    /**
     *  Overridden For Optimisation. Does Nothing.
     *  @param g    The graphics context to use for painting
     */

    public void paintAll(Graphics g) {}


    /**
     *  Overridden For Optimisation. Does Nothing.
     *  @param g    The graphics context to use for painting
     */

    public void update(Graphics g) {}


    /**
     *  Overridden For Optimisation. Does Nothing.
     */

    public void repaint(){}

    /**
     *  Overridden For Optimisation. Does Nothing.
        @param  x -     the x coordinate.
        @param  y -     the y coordinate.
        @param  width - the width.
        @param  height -the height.
    */

    public void repaint(int x, int y, int width, int height){}


    /**
     *  Overridden For Optimisation. Does Nothing.
        @param  tm -    maximum time in milliseconds before update
        @param  x -     the x coordinate.
        @param  y -     the y coordinate.
        @param  width - the width.
        @param  height -the height.
    */

    public void repaint(long tm, int x, int y, int width, int height){}
}