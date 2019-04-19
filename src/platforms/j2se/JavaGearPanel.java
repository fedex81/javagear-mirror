import java.awt.*;
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

public abstract class JavaGearPanel extends Component
    implements MouseListener
{    
    public JavaGearPanel()
    {
        // Add Mouse/Lightgun Support
        addMouseListener(this);
    }

    /**
    *  Gets the preferred size of this component.
    *
    *  @return      A dimension object indicating this component's preferred size.
    */

    public Dimension getPreferredSize()
    {
        return getSize();
    }


    /**
    *  Gets the mininimum size of this component.
    *
    *  @return      A dimension object indicating this component's minimum size.
    */

    public Dimension getMinimumSize()
    {
        return getPreferredSize();
    }
    
    // --------------------------------------------------------------------------------------------
    // Lightgun Handling Code
    // --------------------------------------------------------------------------------------------
    
    public void mousePressed(MouseEvent evt)
    {
        Engine.setLightGunPos(evt.getX(), evt.getY());
    }
    
    public void mouseReleased(MouseEvent evt)
    {
        Engine.setLightGunPos(-1, -1);
    }
    

    public void mouseClicked(MouseEvent evt){}  
    public void mouseEntered(MouseEvent evt){}
    public void mouseExited(MouseEvent evt){}
        
    public boolean isFocusable() { return true; }
}
