/**
 * Ports.java
 *
 * A class to attach virtual devices to the Z80's ports.
 *
 * @author Copyright (c) 2002-2008 Chris White
 * @version 19th May 2008
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

public final class Ports
{
    /** Reference to VDP */
    private Vdp vdp;
    
    /** Reference to PSG */
    private SN76489 psg;
    
    /** European / Domestic System */
    private static int europe = 0x40;
    
    /** Horizontal Counter Latch */
    private int hCounter;
    
    /** I/O Ports A and B * (5 ints each) */
    public int[] ioPorts;
    
    private final static int
        IO_TR_DIRECTION = 0,
        IO_TH_DIRECTION = 1,
        IO_TR_OUTPUT = 2,
        IO_TH_OUTPUT = 3,
        IO_TH_INPUT = 4;
    
    public final static int
        PORT_A = 0,
        PORT_B = 5;
    
    /**
     *  Ports Constructor.
     *
     *  @param v    Vdp
     */

    public Ports(Vdp v, SN76489 p)
    {
        this.vdp = v;
        this.psg = p;
    }
    
    public final void reset()
    {
        if (Setup.LIGHTGUN)
        {
            ioPorts = new int[10];
            ioPorts[PORT_A + IO_TH_INPUT] = 1;
            ioPorts[PORT_B + IO_TH_INPUT] = 1;
        }
        else
        {
            ioPorts = new int[2];
        }
    }

    /**
     *  Output to a Z80 port.
     *
     *  @param port     Port Number
     *  @param value    Value to Output
     */

    public final void out(int port, int value)
    {
        // Game Gear Serial Ports (do nothing for now)
        if (Engine.is_gg && port < 0x07)
        {         
            return;
        }
        
        switch (port & 0xC1)
        {                
            // 0x3F IO Port
            // D7 : Port B TH pin output level (1=high, 0=low)
            // D6 : Port B TR pin output level (1=high, 0=low)
            // D5 : Port A TH pin output level (1=high, 0=low)
            // D4 : Port A TR pin output level (1=high, 0=low)
            // D3 : Port B TH pin direction (1=input, 0=output)
            // D2 : Port B TR pin direction (1=input, 0=output)
            // D1 : Port A TH pin direction (1=input, 0=output)
            // D0 : Port A TR pin direction (1=input, 0=output)
            case 0x01:
                {
                    // Accurate emulation with HCounter
                    if (Setup.LIGHTGUN)
                    {                        
                        boolean oldTH = getTH(PORT_A) != 0 || getTH(PORT_B) != 0;
                        
                        writePort(PORT_A, value);
                        writePort(PORT_B, value >> 2);
                        
                        // Toggling TH latches H Counter
                        if (!oldTH && (getTH(PORT_A) != 0 || getTH(PORT_B) != 0))
                        {
                            hCounter = getHCount();                      
                        }
                    }
                    // Rough emulation of Nationalisation bits
                    else
                    {
                        ioPorts[0] = (value & 0x20) << 1;
                        ioPorts[1] = (value & 0x80);
                        
                        if (europe == 0) // not european system
                        {
                            ioPorts[0] = ~ioPorts[0];
                            ioPorts[1] = ~ioPorts[1];
                        }
                    }
                }
                break;
                
            // 0xBE VDP Data port
            case 0x80:
                vdp.dataWrite(value);
                break;
                
            // 0xBD / 0xBF VDP Control port (Mirrored at two locations)
            case 0x81:
                vdp.controlWrite(value);
                break;
                
            // 0x7F: PSG 
            case 0x40:
            case 0x41:
                if (Platform.SUPPORTS_SOUND && Engine.soundEnabled) psg.write(value);
                break;
        }
    }

    /**
     *  Read from a Z80 Port
     *
     *  @param port     Port Number
     *
     *  @return         Value from Port Number
     */

    public final int in(int port)
    {
        // Game Gear Serial Ports (not fully emulated)
        if (Engine.is_gg && port < 0x07)
        {   
            switch (port)
            {
                // GameGear (Start Button and Nationalisation)
                case 0x00:
                    return (Engine.ggstart & 0xBF) | europe;
    
                // GG Serial Communication Ports  -
                // Return 0 for now as "OutRun" gets stuck in a loop by returning 0xFF
                case 0x01:
                case 0x02:
                case 0x03:
                case 0x04:
                case 0x05:
                    return 0;
                case 0x06:
                    return 0xFF;
            }
        }
        
        
        switch (port & 0xC1)
        {    
            // 0x7E - Vertical Port
            case 0x40:
                return vdp.getVCount();
                
            // 0x7F - Horizontal Port
            case 0x41:
                return hCounter;
   
            // VDP Data port
            case 0x80:
                return vdp.dataRead();
                
            // VDP Control port
            case 0x81:
                return vdp.controlRead();
            
            // 0xC0 / 0xDC - I/O Port A
            // D7 : Port B DOWN pin input
            // D6 : Port B UP pin input
            // D5 : Port A TR pin input
            // D4 : Port A TL pin input
            // D3 : Port A RIGHT pin input
            // D2 : Port A LEFT pin input
            // D1 : Port A DOWN pin input
            // D0 : Port A UP pin input
            case 0xC0:
                return Engine.controller1;
                
            // 0xC1 / 0xDD - I/O Port B and Misc
            // D7 : Port B TH pin input
            // D6 : Port A TH pin input
            // D5 : Unused
            // D4 : RESET button (1= not pressed, 0= pressed)
            // D3 : Port B TR pin input
            // D2 : Port B TL pin input
            // D1 : Port B RIGHT pin input
            // D0 : Port B LEFT pin input
            case 0xC1:
                if (Setup.LIGHTGUN)
                {
                    if (Engine.lightgunClick)
                        lightPhaserSync();

                    return (Engine.controller2 & 0x3F) | (getTH(PORT_A) != 0 ? 0x40 : 0) | (getTH(PORT_B) != 0 ? 0x80 : 0);
                }
                else
                {
                    return (Engine.controller2 & 0x3F) | ioPorts[0] | ioPorts[1];
                }
        }

        // Default Value is 0xFF
        return 0xFF;
    }
    
    // --------------------------------------------------------------------------------------------
    // Port A/B Emulation
    // --------------------------------------------------------------------------------------------
    
    private final void writePort(int index, int value)
    {
        ioPorts[index + IO_TR_DIRECTION] = value & 0x01;
        ioPorts[index + IO_TH_DIRECTION] = value & 0x02;
        ioPorts[index + IO_TR_OUTPUT] = value & 0x10;
        ioPorts[index + IO_TH_OUTPUT] = europe == 0 ? (~value) & 0x20 : value & 0x20;
    }
    
    private final int getTH(int index)
    {
        return (ioPorts[index + IO_TH_DIRECTION] == 0) ? 
                ioPorts[index + IO_TH_OUTPUT] :
                ioPorts[index + IO_TH_INPUT];
    }
     
    private final void setTH(int index, boolean on)
    {
       ioPorts[index + IO_TH_DIRECTION] = 1;
       ioPorts[index + IO_TH_INPUT] = on ? 1 : 0; 
    }

    // --------------------------------------------------------------------------------------------
    // H Counter Emulation
    //
    //  The H counter is 9 bits, and reading it returns the upper 8 bits. This is
    //  because a scanline consists of 342 pixels, which couldn't be represented
    //  with an 8-bit counter. Each scanline is divided up as follows:
    //
    //    Pixels H.Cnt   Description
    //    256 : 00-7F : Active display
    //     15 : 80-87 : Right border
    //      8 : 87-8B : Right blanking
    //     26 : 8B-ED : Horizontal sync
    //      2 : ED-EE : Left blanking
    //     14 : EE-F5 : Color burst
    //      8 : F5-F9 : Left blanking
    //     13 : F9-FF : Left border
    // --------------------------------------------------------------------------------------------
    
    private final int getHCount()
    {        
        int pixels = (Z80.getCycle() * Vdp.SMS_X_PIXELS) / Engine.cyclesPerLine;
        int v = ((pixels - 8) >> 1);
        if (v > 0x93)
            v += 0xE9 - 0x94;
        
        return v & 0xFF;
    }
    
    // --------------------------------------------------------------------------------------------
    // Lightgun <-> Port Synchronisation
    // This is a hacky way to do things, but works reasonably well.
    // --------------------------------------------------------------------------------------------
    
    /** X range of Lightgun */
    private final static int X_RANGE = 48;
    
    /** Y range of Lightgun */
    private final static int Y_RANGE = 4;
       
    private final void lightPhaserSync()
    {  
        int oldTH = getTH(PORT_A);
        int hc = getHCount();
        
        int dx = Engine.lightgunX - (hc << 1);
        int dy = Engine.lightgunY - vdp.line;
        
        // Within 8 pixels of click on Y value
        // Within 96 pixels of click on X value
        if ((dy > -Y_RANGE && dy < Y_RANGE) &&
            (dx > -X_RANGE && dx < X_RANGE))
        {
            setTH(PORT_A, false);
            
            // TH has been toggled, update with lightgun position
            if (oldTH != getTH(PORT_A))
                hCounter = 20 + (Engine.lightgunX >> 1);             
        }
        else
        {
            setTH(PORT_A, true);
            
            // TH has been toggled, update with usual HCounter value
            if (oldTH != getTH(PORT_A))
                hCounter = hc;
        }
    }

    /**
     *  Set Console to European / Japanese Model
     *
     *  @param value    True is European, False is Japanese
     */

    public final static void setDomestic(boolean value)
    {
        europe = value ? 0x40 : 0;
    }
    
    public final static boolean isDomestic()
    {
        return europe != 0;
    }
}