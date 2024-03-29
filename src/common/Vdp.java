/**
 * Vdp.java
 *
 * SMS and GG VDP Emulation.
 *
 * @author Copyright (c) 2002-2008 Chris White
 * @version 19th May 2008
 * 
 * SMS uses 256x192 window (32x28)
 * GG  uses 160x140 window (20x17.5)
 * 
 * What's emulated:
 * Passes Flubba's VDPTEST.SMS utility
 * 
 * Notes:
 * - http://www.smspower.org/forums/viewtopic.php?p=44198
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

public final class Vdp
{   
    // --------------------------------------------------------------------------------------------
    // Screen Dimensions
    // --------------------------------------------------------------------------------------------
    
    public final static int 
        NTSC = 0,
        PAL = 1;
    
    /** NTSC / PAL Emulation */
    public static int videoMode = NTSC;
     
    /** X Pixels, including blanking */
    public final static int SMS_X_PIXELS = 342;
    
    /** Y Pixels (NTSC), including blanking */
    public final static int SMS_Y_PIXELS_NTSC = 262;
    
    /** Y Pixels (PAL), including blanking */
    public final static int SMS_Y_PIXELS_PAL = 313;
     
    /** SMS Visible Screen Width */
    public final static int SMS_WIDTH  = 256;
    
    /** SMS Visible Screen Height */
    public final static int SMS_HEIGHT = 192;   
    
    /** GG Visible Screen Width */
    public final static int GG_WIDTH   = 160;
    
    /** GG Visible Screen Height */
    public final static int GG_HEIGHT  = 144;   
    
    /** GG Visible Window Starts Here (x) */
    public final static int GG_X_OFFSET = 48;
    
    /** GG Window Starts Here (y) */
    public final static int GG_Y_OFFSET = 24;    
    
    // --------------------------------------------------------------------------------------------
    // VDP Emulation
    // --------------------------------------------------------------------------------------------
    
    /** Video RAM */
    public final byte[] VRAM;
    
    /** Colour RAM */
    private final int[] CRAM;

    /** VDP Registers */
    private final int vdpreg[];
    
    /** Status Register */
    private int status;
    
    private final static int 
        STATUS_VINT      = 0x80, // Frame Interrupt Pending
        STATUS_OVERFLOW  = 0x40, // Sprite Overflow
        STATUS_COLLISION = 0x20, // Sprite Collision
        STATUS_HINT      = 0x04; // Line interrupt Pending

    /** First or Second Byte of Command Word */
    private boolean firstByte;
    
    /** Command Word First Byte Latch */
    private int commandByte; 
    
    /** Location in VRAM */
    private int location;
    
    /** Store type of operation taking place */
    private int operation;
    
    /** Buffer VRAM Reads */
    private int readBuffer;

    /** Current Line Number to Render */
    public int line;
    
    /** Vertical Line Interrupt Counter */
    private int counter;

    /** Background Priorities */
    private final boolean bgPriority[];
    
    /** Sprite Collisions */
    private boolean spriteCol[];
 
    /** Address of background table (32x28x2 = 0x700 bytes) */
    private int bgt;
    
    /** This would be different in 224 line mode */
    private final static int BGT_LENGTH = 32 * 28 * 2;
    
    /** As vscroll cannot be changed during the active display period */
    private int vScrollLatch;
    
    // --------------------------------------------------------------------------------------------
    // Emulation Related
    // --------------------------------------------------------------------------------------------
    
    /** Emulated display */
    private final int display[];
    
    /** SMS Colours converted to Java */
    private static int[] SMS_JAVA;
    
    /** GG Colours converted to Java */
    private static int[] GG_JAVA1, GG_JAVA2;
    
    /** Horizontal Viewport Start */
    public static int h_start;      
    
    /** Horizontal Viewport End */
    public static int h_end;    
        
    // --------------------------------------------------------------------------------------------
    // Decoded SAT Table
    // --------------------------------------------------------------------------------------------
    
    /** Address of sprite attribute table (256 bytes) */
    private int sat;
    
    /** Determine whether SAT has been written to */
    private boolean isSatDirty;
    
    /** Max number of sprites hardware can handle per scanline */
    private final static int SPRITES_PER_LINE = 8;
    
    /** Decoded SAT by each scanline */
    private final int lineSprites[][] = new int[SMS_HEIGHT][1 + (3 * SPRITES_PER_LINE)];
    
    /** References into lineSprites table */
    private final static int
        SPRITE_COUNT = 0,   // Number of sprites on line
        
        SPRITE_X = 1,       // Sprite X Position
        SPRITE_Y = 2,       // Sprite Y Position
        SPRITE_N = 3;       // Sprite Pattern
    
    // --------------------------------------------------------------------------------------------
    // Decoded Tiles
    // --------------------------------------------------------------------------------------------
    
    /** Total number of tiles in VRAM */
    private final static int TOTAL_TILES = 512;
    
    /** Tile size */
    private final static int TILE_SIZE = 8;
    
    /** Decoded tile data */
    private int[][] tiles;
    
    /** Store whether tile has been written to */
    private boolean[] isTileDirty;
    
    /** Min / Max of dirty tile index */
    private int minDirty, maxDirty;

    // --------------------------------------------------------------------------------------------
    
    
    /**
     *  Vdp Constructor.
     *
     *  @param d    Pointer to Generated Display
     */

    public Vdp(int[] d)
    {
        this.display = d;

        // 16K of Video RAM
        VRAM = new byte[0x4000];

        // Note, we don't directly emulate CRAM but actually store the converted Java palette
        // in it. Therefore the length is different to on the real GameGear where it's actually 
        // 64 bytes.
        CRAM = new int[0x20];

        // 15 Registers, (0-10) used by SMS, but some programs write > 10
        vdpreg = new int[16];

        bgPriority = new boolean[SMS_WIDTH];
        
        if (Setup.VDP_SPRITE_COLLISIONS)
            spriteCol = new boolean[SMS_WIDTH];

        createCachedImages();
    }

    /**
     *  Reset VDP.
     */

    public final void reset()
    {
        generateConvertedPals();
        
        firstByte = true;

        location = 0;
        counter = 0;
        status = 0;
        operation = 0;
        vdpreg[0] = 0;
        vdpreg[1] = 0;
        vdpreg[2] = 0x0E;       // B1-B3 high on startup
        vdpreg[3] = 0;
        vdpreg[4] = 0;
        vdpreg[5] = 0x7E;       // B1-B6 high on startup
        vdpreg[6] = 0;
        vdpreg[7] = 0;
        vdpreg[8] = 0;
        vdpreg[9] = 0;
        vdpreg[10] = 0;
        
        vScrollLatch = 0;

        Z80.interruptLine = false;
        
        isSatDirty = true;
        
        minDirty = TOTAL_TILES;
        maxDirty = -1;
    }
    
    /** 
     * Force full redraw of entire cache
     */
    
    public final void forceFullRedraw()
    {        
        bgt = (vdpreg[2] & 0x0f &~0x01) << 10;
        minDirty = 0;
        maxDirty = TOTAL_TILES - 1;
        for (int i = isTileDirty.length; i-- != 0;)
            isTileDirty[i] = true;
        
        sat = (vdpreg[5] &~0x01 &~0x80) << 7;
        isSatDirty = true;       
    }

    /**
     *  Read Vertical Port
     *
     *  @return             VCounter Value
     */

    public final int getVCount()
    {
        if (videoMode == NTSC)
        {
            if (line > 0xDA) // Values from 00 to DA, then jump to D5-FF
                return line-6;
        }

        // PAL
        else
        {
            if (line > 0xF2)
                return line-0x39;
        }

        return line;
    }

    /**
     *  Read VDP Control Port (0xBF)
     *
     *  @return     Copy of Status Register
     */

    public final int controlRead()
    {
        // Reset flag
        firstByte = true;

        // Create copy, as we'll need to clear bits of status reg
        int statuscopy = status;

        // Clear b7, b6, b5 when status register read
        status = 0; // other bits never used anyway

        // Clear IRQ Line
        Z80.interruptLine = false;

        return statuscopy;
    }

    /**
     *  Write to VDP Control Port (0xBF)
     *
     *  @param  value   Value to Write
     */

    public final void controlWrite(int value)
    {
         // Store First Byte of Command Word
        if (firstByte)
        {
            firstByte = false;
            commandByte = value;
            location = (location & 0x3F00) | value;
        }
        else
        {
            firstByte = true;
            operation = (value >> 6) & 3;
            location = commandByte | (value << 8);

            // Read value from VRAM
            if (operation == 0)
            {
                readBuffer = VRAM[(location++) & 0x3FFF]&0xFF;
            }
            // Set VDP Register
            else if (operation == 2)
            {
                int reg = (value & 0x0F);
                
                switch (reg)
                {
                    // Interrupt Control 0 (Verified using Charles MacDonald test program)
                    // Bit 4 of register $00 acts like a on/off switch for the VDP's IRQ line.
                    
                    // As long as the line interrupt pending flag is set, the VDP will assert the
                    // IRQ line if bit 4 of register $00 is set, and it will de-assert the IRQ line
                    // if the same bit is cleared.
                    case 0:
                        if (Setup.ACCURATE_INTERRUPT_EMULATION && (status & STATUS_HINT) != 0)
                            Z80.interruptLine = (commandByte & 0x10) != 0;
                        break;
                    
                    // Interrupt Control 1
                    case 1:
                        if (((status & STATUS_VINT) != 0) && (commandByte & 0x20) != 0)
                            Z80.interruptLine = true;
                        
                        // By writing here we've updated the height of the sprites and need to update 
                        // the sprites on each line
                        if ((commandByte & 3) != (vdpreg[reg] & 3))
                            isSatDirty = true;
                        break;
                        
                    // BGT Written
                    case 2:
                        // Address of Background Table in VRAM
                        bgt = (commandByte &0x0f &~0x01) << 10;
                        break;
                        
                    // SAT Written
                    case 5:
                    {
                        int old = sat;
                        // Address of Sprite Attribute Table in RAM
                        sat = (commandByte &~ 0x01 &~ 0x80) << 7;
                        
                        if (old != sat)
                        {
                            // Should also probably update tiles here?
                            isSatDirty = true;                          
                            //System.out.println("New address written to SAT: "+old + " -> " + sat);
                        }
                            
                    }
                        break;              
                }               
                vdpreg[reg] = commandByte; // Set reg to previous byte
            }
        }
    }

    /**
     *  Read VDP Data Port (0xBE)
     *
     *  @return     Buffered read from VRAM
     */

    public final int dataRead() // 0xBE
    {
        firstByte = true; // Reset flag

        int value = readBuffer; // Stores value to be returned
        readBuffer = VRAM[(location++) & 0x3FFF] & 0xFF;           

        return value;
    }

    /**
     *  Write to VDP Data Port (0xBE)
     *
     *  @param  value   Value to Write
     */

    public final void dataWrite(int value)
    {
         // Reset flag
        firstByte = true;

        switch(operation)
        {
            // VRAM Write
            case 0x00:
            case 0x01:
            case 0x02:
                {
                    int address = location & 0x3FFF;
                    // Check VRAM value has actually changed
                    if (value != (VRAM[address] & 0xFF))
                    {
                        //if (address >= bgt && address < bgt + BGT_LENGTH); // Don't write dirty to BGT
                        if (address >= sat && address < sat+64) // Don't write dirty to SAT
                            isSatDirty = true;
                        else if (address >= sat+128 && address < sat+256)
                            isSatDirty = true;
                        else 
                        {
                            int tileIndex = address >> 5;
                       
                            // Get tile number that's being written to (divide VRAM location by 32)
                            isTileDirty[tileIndex] = true;  
                            if (tileIndex < minDirty) minDirty = tileIndex;
                            if (tileIndex > maxDirty) maxDirty = tileIndex; 
                        }
                        
                        VRAM[address] = (byte) value;
                    }
                }
                
                break;
            // CRAM Write
            // Instead of writing real colour to CRAM, write converted Java palette colours for speed.
            // Slightly inaccurate, as CRAM doesn't contain real values, but it is never read by software.      
            case 0x03:
                if (Engine.is_sms)
                    CRAM[location & 0x1F] = SMS_JAVA[value & 0x3F];
                else if (Engine.is_gg)
                {
                    if ((location & 1) == 0) // first byte
                        CRAM[(location & 0x3F)>>1] = GG_JAVA1[value]; // GG
                    else
                        CRAM[(location & 0x3F)>>1] |= GG_JAVA2[value & 0x0F];
                        
                }
                break;
        }

        if (BuildSettings.ACCURATE)
            readBuffer = value;
        
        location++;
    }

    /**
     *  Generate VDP Interrupts.
     *  Assert the IRQ line as necessary for a particular scanline.
     *
     *  @param  lineno  Line to check for interrupts
     *  
     *  @see http://www.smspower.org/forums/viewtopic.php?t=9366&highlight=chicago
     */

    public final void interrupts(int lineno)
    {
        if (lineno <= 192)
        {
            // This can cause hangs as interrupts are only taken between instructions, 
            // if the IRQ status flag is set *during* the execution of an instruction the 
            // CPU will be able to read it without the interrupt occurring.
            //
            // e.g. Chicago Syndicate on GG
            
            if (!Setup.ACCURATE_INTERRUPT_EMULATION && lineno == 192)
                status |= STATUS_VINT; 
            
            // Counter Expired = Line Interrupt Pending
            if (counter == 0)
            {
                // Reload Counter
                counter = vdpreg[10];
                status |= STATUS_HINT;  
            }
            // Otherwise Decrement Counter
            else counter--;

            // Line Interrupts Enabled and Pending. Assert IRQ Line.
            if (((status & STATUS_HINT) != 0) && ((vdpreg[0] & 0x10) != 0))
                Z80.interruptLine = true;
        }

        // lineno >= 193
        else
        {    
            // Reload counter on every line outside active display + 1
            counter = vdpreg[10];

            // Frame Interrupts Enabled and Pending. Assert IRQ Line.
            if (((status & STATUS_VINT) != 0) && ((vdpreg[1] & 0x20) != 0) && (lineno < 224))
                Z80.interruptLine = true;
            
            // Update the VSCROLL latch for the next active display period
            if (BuildSettings.ACCURATE && lineno == Engine.no_of_scanlines - 1)
                vScrollLatch = vdpreg[9];
        }
    }
    
    public final void setVBlankFlag()
    {
        status |= STATUS_VINT;    
    }
    
    /**
     *  Render Line of SMS/GG Display
     *
     *  @param  lineno  Line Number to Render
     */

    public final void drawLine(int lineno)
    {
        // ----------------------------------------------------------------------------------------
        // Check we are in the visible drawing region
        // ----------------------------------------------------------------------------------------
        if (Engine.is_gg)
        {
            if (lineno < GG_Y_OFFSET || lineno >= GG_Y_OFFSET + GG_HEIGHT)
                return;
        }
        
        // ----------------------------------------------------------------------------------------
        // Clear sprite collision array if enabled
        // ----------------------------------------------------------------------------------------
        if (Setup.VDP_SPRITE_COLLISIONS)
        {
            for (int i = spriteCol.length; i-- != 0;)
                spriteCol[i] = false;
        }

        // ----------------------------------------------------------------------------------------
        // Check Screen is switched on
        // ----------------------------------------------------------------------------------------
        if ((vdpreg[1] & 0x40) != 0)
        {
            // ------------------------------------------------------------------------------------
            // Draw Background Layer
            // ------------------------------------------------------------------------------------
            if (maxDirty != -1)
                decodeTiles();
            
            drawBg(lineno);

            // ------------------------------------------------------------------------------------
            // Draw Sprite Layer
            // ------------------------------------------------------------------------------------
            if (isSatDirty)
                decodeSat();
            
            if (lineSprites[lineno][SPRITE_COUNT] != 0)
                drawSprite(lineno);

            // ------------------------------------------------------------------------------------
            // Blank Leftmost Column (SMS Only)
            // ------------------------------------------------------------------------------------
            if (Engine.is_sms && (vdpreg[0] & 0x20) != 0)
            {
                int colour = CRAM[16 + (vdpreg[7] & 0x0F)];
                int location = lineno << 8;
                
                // Don't use a loop here for speed purposes
                display[location++] = colour;
                display[location++] = colour;
                display[location++] = colour;
                display[location++] = colour;
                display[location++] = colour;
                display[location++] = colour;
                display[location++] = colour;
                display[location] = colour; 
            }
        }
        // ----------------------------------------------------------------------------------------
        // Blank Display
        // ----------------------------------------------------------------------------------------
        else
        {
            drawBGColour(lineno);
        }
    }
    
    private final void drawBg(int lineno)
    {
        // Horizontal Scroll
        int hscroll = vdpreg[8];
        
        // Vertical Scroll
        int vscroll = BuildSettings.ACCURATE ? vScrollLatch : vdpreg[9];
        
        // Top Two Rows Not Affected by Horizontal Scrolling (SMS Only)
        // We don't actually need the SMS check here as we don't draw this line for GG now
        if (lineno < 16 && ((vdpreg[0] & 0x40) != 0) /*&& Setup.is_sms*/)
            hscroll = 0;
        
        // Lock Right eight columns
        int lock = vdpreg[0] & 0x80;
        
        // Column to start drawing at (0 - 31) [Add extra columns for GG]
        int tile_column = (32 - (hscroll >> 3)) + h_start;
        
        // Row to start drawing at (0 - 27)
        int tile_row = (lineno + vscroll) >> 3;
        
        if (tile_row > 27)
            tile_row -= 28;
        
        // Actual y position in tile (0 - 7) (Also times by 8 here for quick access to pixel)
        int tile_y = ((lineno + (vscroll & 7)) & 7) << 3;

        // Array Position
        int rowprecal = lineno << 8;
        
        // Cycle through background table 
        for (int tx = h_start; tx < h_end; tx++)
        {           
            int tile_props = bgt + ((tile_column & 0x1F) << 1) + (tile_row << 6);
            int secondbyte = VRAM[tile_props+1];

            // Select Palette (Either 0 or 16)
            int pal = (secondbyte & 0x08) << 1;
            
            // Screen X Position
            int sx = (tx << 3) + (hscroll & 7);
            
            // Do V-Flip (take into account the fact that everything is times 8)
            int pixY = ((secondbyte & 0x04) == 0) ? tile_y : ((7 << 3) - tile_y);
            
            // Pattern Number (0 - 512)
            int[] tile = tiles[(VRAM[tile_props] & 0xFF) + ((secondbyte & 0x01) << 8)];
            
            // -----------------------------------------------------------------------------------          
            // Plot 8 Pixel Row (No H-Flip)
            // -----------------------------------------------------------------------------------
            if ((secondbyte & 0x02) == 0)
            {
                for (int pixX = 0; pixX < 8 && sx < SMS_WIDTH; pixX++, sx++)
                {
                    int colour = tile[pixX + pixY];

                    // Set Priority Array (Sprites over/under background tile)
                    bgPriority[sx] = ((secondbyte & 0x10) != 0) && (colour != 0);  
                    display[sx + rowprecal] = CRAM[colour+pal];
                }               
            }
            // -----------------------------------------------------------------------------------
            // Plot 8 Pixel Row (H-Flip)
            // -----------------------------------------------------------------------------------
            else
            {
                for (int pixX = 7; pixX >= 0 && sx < SMS_WIDTH; pixX--, sx++)
                {
                    int colour = tile[pixX + pixY];

                    // Set Priority Array (Sprites over/under background tile)
                    bgPriority[sx] = ((secondbyte & 0x10) != 0) && (colour != 0);  
                    display[sx + rowprecal] = CRAM[colour+pal];
                }                   
            }
            tile_column++;
            
            // ------------------------------------------------------------------------------------
            // Rightmost 8 columns Not Affected by Vertical Scrolling
            // ------------------------------------------------------------------------------------
            if (lock != 0 && tx == 23)
            {
                tile_row = lineno >> 3;
                tile_y = (lineno & 7) << 3;
            }           
        }
    }
    
    /**
     *  Render Line of Sprite Layer
     *
     * - Notes: Sprites do not wrap on the x-axis.
     *
     *  @param  lineno  Line Number to Render
     */

    private final void drawSprite(int lineno)
    {
        // Reference to the sprites that should appear on this line
        int sprites[] = lineSprites[lineno];
        
        // Number of sprites to draw on this scanline
        int count = Math.min(SPRITES_PER_LINE, sprites[SPRITE_COUNT]);
                
        // Zoom Sprites (0 = off, 1 = on)
        int zoomed = vdpreg[1] & 0x01;
    
        int row_precal = lineno << 8;
            
        // Get offset into array
        int off = (count * 3);
        
        // Have to iterate backwards here as we've already cached tiles
        for (int i = count; i-- != 0;)
        {           
            // Sprite Pattern Index
            // Also mask on Pattern Index from 100 - 1FFh (if reg 6 bit 3 set)
            int n = sprites[off--] | ((vdpreg[6] & 0x04) << 6);
            
            // Sprite Y Position
            int y = sprites[off--];
            
            // Sprite X Position
            // Shift pixels left by 8 if necessary
            int x = sprites[off--] - (vdpreg[0] & 0x08);
            
            // Row of tile data to render (0-7)
            int tileRow = (lineno - y) >> zoomed;
            
            // When using 8x16 sprites LSB has no effect
            if ((vdpreg[1] & 0x02) != 0)
                n &= ~0x01;

            // Pattern Number (0 - 512)
            int[] tile = tiles[n + ((tileRow & 0x08) >> 3)];

            // If X Co-ordinate is negative, do a fix to draw from position 0
            int pix = 0;
            
            if (x < 0)
            {
                pix = (-x);
                x = 0;
            }
            
            // Offset into decoded tile data
            int offset = pix + ((tileRow & 7) << 3);
            
            // --------------------------------------------------------------------------------
            // Plot Normal Sprites (Width = 8)
            // --------------------------------------------------------------------------------
            if (zoomed == 0)
            {
                for (; pix < 8 && x < SMS_WIDTH; pix++, x++)
                {
                    int colour = tile[offset++];
                    
                    if (colour != 0 && !bgPriority[x])
                    {
                        display[x + row_precal] = CRAM[colour+16];
                        
                        // Emulate sprite collision (when two opaque pixels overlap)
                        if (Setup.VDP_SPRITE_COLLISIONS)
                        {
                            if (!spriteCol[x])
                                spriteCol[x] = true;
                            else
                                status |= 0x20; // Bit 5 of status flag indicates collision
                        }                       
                    }   
                }
            }
            // --------------------------------------------------------------------------------
            // Plot Zoomed Sprites (Width = 16)
            // --------------------------------------------------------------------------------
            else
            { 
                for (; pix < 8 && x < SMS_WIDTH; pix++, x += 2)
                {
                    int colour = tile[offset++]; 
                    
                    // Plot first pixel
                    if (colour != 0 && !bgPriority[x])
                    {
                        display[x + row_precal] = CRAM[colour+16];
                        
                        if (Setup.VDP_SPRITE_COLLISIONS)
                        {
                            if (!spriteCol[x])
                                spriteCol[x] = true;
                            else
                                status |= 0x20; // Bit 5 of status flag indicates collision
                        }                       
                    }
                    
                    // Plot second pixel    
                    if (colour != 0 && !bgPriority[x+1])
                    {
                        display[x + row_precal + 1] = CRAM[colour+16];
                        
                        if (Setup.VDP_SPRITE_COLLISIONS)
                        {
                            if (!spriteCol[x+1])
                                spriteCol[x+1] = true;
                            else
                                status |= 0x20; // Bit 5 of status flag indicates collision
                        }                       
                    }                   
                }
            }   
        }
        
        // Sprite Overflow (more than 8 sprites on line)
        if (sprites[SPRITE_COUNT] >= SPRITES_PER_LINE)
        {
            status |= 0x40;
        }
    }


    /**
     *  Draw a Line of the current Background Colour
     *
     *  @param  lineno  Line Number to Render
     */

    private final void drawBGColour(int lineno)
    {
        int colour = CRAM[16 + (vdpreg[7]&0x0F)];
        int row_precal = lineno << 8;

        for (int x = SMS_WIDTH; x-- != 0;)
            display[row_precal++] = colour;
    }

    
    // --------------------------------------------------------------------------------------------
    // Generated pre-converted palettes.
    //
    // SMS and GG colours are converted to Java RGB for speed purposes
    //
    // Java: 0xAARRGGBB (4 bytes) Java colour
    //
    // SMS : 00BBGGRR   (1 byte)
    // GG  : GGGGRRRR   (1st byte)
    //       0000BBBB   (2nd byte)
    // --------------------------------------------------------------------------------------------
       
    private final void generateConvertedPals()
    {
        if (Engine.is_sms && SMS_JAVA == null)
        {
            SMS_JAVA = new int[0x40];
            
            for (int i = 0; i < SMS_JAVA.length; i++)
            {
                int r = i & 0x03;
                int g = (i >> 2) & 0x03;
                int b = (i >> 4) & 0x03;
                
                SMS_JAVA[i] = ((r * 85) << 16) | ((g * 85) << 8) | (b * 85);
            }
        }
        else if (Engine.is_gg && GG_JAVA1 == null)
        {
            GG_JAVA1 = new int[0x100];
            GG_JAVA2 = new int[0x10];
            
            // Green & Blue
            for (int i = 0; i < GG_JAVA1.length; i++)
            {           
                int g = i & 0x0F;
                int b = (i >> 4) & 0x0F;   
                
                // Shift and fill with the original bitpattern
                // so %1111 becomes %11111111, %1010 becomes %10101010
                GG_JAVA1[i] = (g << 20) | (g << 16) | (b << 12) | (b << 8);
            }
            
            // Red
            for (int i = 0; i < GG_JAVA2.length; i++)
            {           
                GG_JAVA2[i] = (i << 4) | i;
            }       
        }
    }
    
    // --------------------------------------------------------------------------------------------
    // Decode all background tiles
    //
    // Tiles are 8x8
    // 
    // Background table is a 32x28 matrix of words stored in VRAM
    //
    //  MSB          LSB
    //  ---pcvhnnnnnnnnn
    //
    // p = priority
    // c = palette
    // v = vertical flip
    // h = horizontal flip
    // n = pattern index (0 - 512)
    // --------------------------------------------------------------------------------------------
        
    private final void createCachedImages()
    {
         tiles = new int[TOTAL_TILES][TILE_SIZE * TILE_SIZE];
         isTileDirty = new boolean[TOTAL_TILES];
    }
    
    // Note we should try not to update the bgt/sat locations?
    private final void decodeTiles()
    {   
        //System.out.println("["+line+"]"+" min dirty:" +minDirty+" max: "+maxDirty);

        for (int i = minDirty; i <= maxDirty; i++)
        {
            // Only decode tiles that have changed since the last iteration
            if (!isTileDirty[i]) continue;
            
            // Note that we've updated the tile
            isTileDirty[i] = false;
            
            //System.out.println("tile "+i+" is dirty");
            int tile[] = tiles[i];
            
            int pixel_index = 0;

            // 4 bytes per row, total of 32 bytes per tile
            int address = (i << 5);
            
            // Plot column of 8 pixels
            for (int y = 0; y < TILE_SIZE; y++)
            {       
                int address0 = VRAM[address++];
                int address1 = VRAM[address++];
                int address2 = VRAM[address++];
                int address3 = VRAM[address++];
                            
                // Plot row of 8 pixels
                for (int bit = 0x80; bit != 0; bit>>=1)
                {
                    int colour = 0;
                    
                    // Set Colour of Pixel (0-15)
                    if ((address0 & bit) != 0) colour |= 0x01;
                    if ((address1 & bit) != 0) colour |= 0x02;
                    if ((address2 & bit) != 0) colour |= 0x04;
                    if ((address3 & bit) != 0) colour |= 0x08;  
                    
                    tile[pixel_index++] = colour;
                }   
            }
        }
        
        // Reset min/max dirty counters
         minDirty = TOTAL_TILES;
         maxDirty = -1;
    }
    
    // --------------------------------------------------------------------------------------------
    //
    //  DECODE SAT TABLE 
    //
    //   Each sprite is defined in the sprite attribute table (SAT), a 256-byte
    //   table located in VRAM. The SAT has the following layout:
    //
    //      00: yyyyyyyyyyyyyyyy
    //      10: yyyyyyyyyyyyyyyy
    //      20: yyyyyyyyyyyyyyyy
    //      30: yyyyyyyyyyyyyyyy
    //      40: ????????????????
    //      50: ????????????????
    //      60: ????????????????
    //      70: ????????????????
    //      80: xnxnxnxnxnxnxnxn
    //      90: xnxnxnxnxnxnxnxn
    //      A0: xnxnxnxnxnxnxnxn
    //      B0: xnxnxnxnxnxnxnxn
    //      C0: xnxnxnxnxnxnxnxn
    //      D0: xnxnxnxnxnxnxnxn
    //      E0: xnxnxnxnxnxnxnxn
    //      F0: xnxnxnxnxnxnxnxn
    //
    //   y = Y coordinate + 1
    //   x = X coordinate
    //   n = Pattern index
    //   ? = Unused 
    // --------------------------------------------------------------------------------------------
    
    
    /**
     * Creates a list of sprites per scanline 
     */
    
    private final void decodeSat()
    {       
        isSatDirty = false;
        
        // ----------------------------------------------------------------------------------------
        // Clear Existing Table
        // ----------------------------------------------------------------------------------------
        
        for (int i = lineSprites.length; i-- != 0;)
            lineSprites[i][SPRITE_COUNT] = 0;
                
        // Height of Sprites (8x8 or 8x16)
        int height = (vdpreg[1] & 0x02) == 0 ? 8 : 16;

        // Enable Zoomed Sprites
        if ((vdpreg[1] & 0x01) == 0x01)
        {
            height <<= 1;
        }
        
        // ----------------------------------------------------------------------------------------
        // Search Sprite Attribute Table (64 Bytes)
        // ----------------------------------------------------------------------------------------
        for (int spriteno = 0; spriteno < 0x40; spriteno++)
        {
            // Sprite Y Position
            int y = VRAM[sat + spriteno]&0xFF;
            
            // VDP stops drawing if y == 208
            if (y == 208)
            {
                return;
            }
            
            // y is actually at +1 of value
            y++;
            
            // If off screen, draw from negative 16 onwards
            if (y > 240)
            {
                y -= 256;
            }   
                    
            for (int lineno = 0; lineno < SMS_HEIGHT; lineno++)
            {   
                // --------------------------------------------------------------------------------
                // Does Sprite fall on this line?
                // -------------------------------------------------------------------------------- 
                if ((lineno >= y) && ((lineno-y) < height))
                {           
                    int[] sprites = lineSprites[lineno];
                    
                    if (sprites[SPRITE_COUNT] < SPRITES_PER_LINE)
                    {
                        // Get offset into array
                        int off = (sprites[SPRITE_COUNT] * 3) + SPRITE_X;
                        
                        // Address of Sprite in Sprite Attribute Table
                        int address = sat + (spriteno<<1) + 0x80;
                                                
                        // Sprite X Position
                        sprites[off++] = (VRAM[address++] & 0xFF);
                        
                        // Sprite Y Position
                        sprites[off++] = y;
                        
                        // Sprite Pattern Index
                        sprites[off++] = (VRAM[address] & 0xFF);
                        
                        // Increment number of sprites on this scanline
                        sprites[SPRITE_COUNT]++;
                    }
                }
            }   
        }
    }  
    
    // --------------------------------------------------------------------------------------------
    // VDP State Saving
    // --------------------------------------------------------------------------------------------
    
    public int[] getState()
    {
        int state[] = new int[3 + vdpreg.length + CRAM.length];
        
        state[0] = videoMode | (status << 8) | (firstByte ? (1 << 16) : 0) | (commandByte << 24);
        state[1] = location | (operation << 16) | (readBuffer << 24);
        state[2] = counter | (vScrollLatch << 8) | (line << 16);
        
        System.arraycopy(vdpreg, 0, state, 3, vdpreg.length);
        System.arraycopy(CRAM, 0, state, 3 + vdpreg.length, CRAM.length);
        
        return state;
    }
    
    public void setState(int[] state)
    {
        int temp = state[0];       
        videoMode = temp & 0xFF;
        status = (temp >> 8) & 0xFF;
        firstByte = ((temp >> 16) & 0xFF) != 0;
        commandByte = (temp >> 24) & 0xFF;
        
        temp = state[1];
        location = temp & 0xFFFF;
        operation = (temp >> 16) & 0xFF;
        readBuffer = (temp >> 24) & 0xFF;
        
        temp = state[2];
        counter = temp & 0xFF;
        vScrollLatch = (temp >> 8) & 0xFF;
        line = (temp >> 16) & 0xFFFF;
        
        System.arraycopy(state, 3, vdpreg, 0, vdpreg.length);
        System.arraycopy(state, 3 + vdpreg.length, CRAM, 0, CRAM.length);
        
        // Force redraw of all cached tile data
        forceFullRedraw();
    }
}