/**
 * Zilog Z80 CPU Emulation
 *
 * @authors 
 * Copyright (C) 2002-2008 Chris White (pointblnk@hotmail.com)
 * Copyright (C) 2002 Erik Duijs (erikduijs@yahoo.com)
 * 
 * @version 6th July 2008
 * 
 * Originally based on my JavaGear university project.
 * Rewritten for MIDP using flag lookup code from the Java Emulation Framework.
 * 
 * Proguard will inline a lot of the final methods automatically.
 * Therefore, when obfuscated the emulation will run faster.
 * 
 * What's emulated:
 * - All documented & undocumented instructions
 * - Passes ZEXALL instruction tester on Sega Master System
 * 
 * What's not emulated:
 * - Various undocumented flags
 * - No wrapping on stack pointer for speed
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

public final class Z80
{
    /** Speedup hack to set tstates to '0' on halt instruction */
    private final static boolean HALT_SPEEDUP = true;
 
    /** Reference to ports attached to Z80 */
    private Ports port;
    
    // --------------------------------------------------------------------------------------------
    // Z80 Internal Stuff
    // --------------------------------------------------------------------------------------------
    
    /** Program Counter */
    private int pc;
    
    /** Stack Pointer */
    private int sp;

    /** Interrupt Mode (0,1,2) */
    private int im;
    
    /** Interrupt Flip Flop 1 */
    private boolean iff1;
    
    /** Interrupt Flip Flop 2 */
    private boolean iff2;

    /** Halt Instruction Called */
    private boolean halt;
    
    /** EI Instruction Called */
    private boolean EI_inst;

    /** Interrupt Line Status */
    public static boolean interruptLine;
    
    /** Interrupt Vector */
    public static int interruptVector;

    // --------------------------------------------------------------------------------------------
    // Registers
    // --------------------------------------------------------------------------------------------

    /** Accumulator Register */
    private int a, a2;
    
    /** BC Register */
    private int b, c, b2, c2;
    
    /** DE Register */
    private int d, e, d2, e2;
    
    /** HL Register */
    private int h, l, h2, l2;
    
    /** IX Register */
    private int ixL, ixH;
    
    /** IY Register */
    private int iyL, iyH;
    
    /** Memory Refresh Register */
    private int r;
    
    /** Interrupt page address register */
    private int i;  

    // --------------------------------------------------------------------------------------------
    // Flag Register
    // --------------------------------------------------------------------------------------------
    
    /** Flag Register */
    private static int f, f2;

    /** carry (set when a standard carry occurred) */
    private final static int F_CARRY     = 0x01;    
    
    /** negative (set when instruction is subtraction, clear when addition) */
    private final static int F_NEGATIVE  = 0x02;
    
    /** true indicates even parity in the result, false for 2s complement sign overflow */
    private final static int F_PARITY    = 0x04;
    
    /** true indicates even parity in the result, false for 2s complement sign overflow */
    private final static int F_OVERFLOW  = 0x04; 
    
    /** bit3 (usually a copy of bit 3 of the result) */
    private final static int F_BIT3      = 0x08; 
    
    /** half carry (set when a carry occured between bit 3 / 4 of result - used for BCD */
    private final static int F_HALFCARRY = 0x10;
    
    /** bit5 (usually a copy of bit 5 of the result) */
    private final static int F_BIT5      = 0x20;
    
    /** zero (set when a result is zero) */
    private final static int F_ZERO      = 0x40;
    
    /** sign (set when a result is negative) */
    private final static int F_SIGN      = 0x80;

    
    // --------------------------------------------------------------------------------------------
    // Opcode timings
    // --------------------------------------------------------------------------------------------

    /** Total number of cycles we're executing for */ 
    private static int totalCycles;
    
    /** TStates remaining */
    public static int tstates;

    private final static short OP_STATES[] = {

                                        /*          0  1  2  3  4  5  6  7  8  9  A  B  C  D  E  F */
                                        /* 0x00 */  4,10, 7, 6, 4, 4, 7, 4, 4,11, 7, 6, 4, 4, 7, 4,
                                        /* 0x10 */  8,10, 7, 6, 4, 4, 7, 4,12,11, 7, 6, 4, 4, 7, 4,
                                        /* 0x20 */  7,10,16, 6, 4, 4, 7, 4, 7,11,16, 6, 4, 4, 7, 4,
                                        /* 0x30 */  7,10,13, 6,11,11,10, 4, 7,11,13, 6, 4, 4, 7, 4,
                                        /* 0x40 */  4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
                                        /* 0x50 */  4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
                                        /* 0x60 */  4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
                                        /* 0x70 */  7, 7, 7, 7, 7, 7, 4, 7, 4, 4, 4, 4, 4, 4, 7, 4,
                                        /* 0x80 */  4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
                                        /* 0x90 */  4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
                                        /* 0xA0 */  4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
                                        /* 0xB0 */  4, 4, 4, 4, 4, 4, 7, 4, 4, 4, 4, 4, 4, 4, 7, 4,
                                        /* 0xC0 */  5,10,10,10,10,11, 7,11, 5,10,10, 0,10,17, 7,11,
                                        /* 0xD0 */  5,10,10,11,10,11, 7,11, 5, 4,10,11,10, 0, 7,11,
                                        /* 0xE0 */  5,10,10,19,10,11, 7,11, 5, 4,10, 4,10, 0, 7,11,
                                        /* 0xF0 */  5,10,10, 4,10,11, 7,11, 5, 6,10, 4,10, 0, 7,11 };

    private final static short OP_CB_STATES[] = {

                                        /*          0  1  2  3  4  5  6  7  8  9  A  B  C  D  E  F */
                                        /* 0x00 */  8, 8, 8, 8, 8, 8,15, 8, 8, 8, 8, 8, 8, 8,15, 8,
                                        /* 0x10 */  8, 8, 8, 8, 8, 8,15, 8, 8, 8, 8, 8, 8, 8,15, 8,
                                        /* 0x20 */  8, 8, 8, 8, 8, 8,15, 8, 8, 8, 8, 8, 8, 8,15, 8,
                                        /* 0x30 */  8, 8, 8, 8, 8, 8,15, 8, 8, 8, 8, 8, 8, 8,15, 8,
                                        /* 0x40 */  8, 8, 8, 8, 8, 8,12, 8, 8, 8, 8, 8, 8, 8,12, 8,
                                        /* 0x50 */  8, 8, 8, 8, 8, 8,12, 8, 8, 8, 8, 8, 8, 8,12, 8,
                                        /* 0x60 */  8, 8, 8, 8, 8, 8,12, 8, 8, 8, 8, 8, 8, 8,12, 8,
                                        /* 0x70 */  8, 8, 8, 8, 8, 8,12, 8, 8, 8, 8, 8, 8, 8,12, 8,
                                        /* 0x80 */  8, 8, 8, 8, 8, 8,15, 8, 8, 8, 8, 8, 8, 8,15, 8,
                                        /* 0x90 */  8, 8, 8, 8, 8, 8,15, 8, 8, 8, 8, 8, 8, 8,15, 8,
                                        /* 0xA0 */  8, 8, 8, 8, 8, 8,15, 8, 8, 8, 8, 8, 8, 8,15, 8,
                                        /* 0xB0 */  8, 8, 8, 8, 8, 8,15, 8, 8, 8, 8, 8, 8, 8,15, 8,
                                        /* 0xC0 */  8, 8, 8, 8, 8, 8,15, 8, 8, 8, 8, 8, 8, 8,15, 8,
                                        /* 0xD0 */  8, 8, 8, 8, 8, 8,15, 8, 8, 8, 8, 8, 8, 8,15, 8,
                                        /* 0xE0 */  8, 8, 8, 8, 8, 8,15, 8, 8, 8, 8, 8, 8, 8,15, 8,
                                        /* 0xF0 */  8, 8, 8, 8, 8, 8,15, 8, 8, 8, 8, 8, 8, 8,15, 8 };

    private final static short OP_DD_STATES[] = {

                                        /*          0  1  2  3  4  5  6  7  8  9  A  B  C  D  E  F */
                                        /* 0x00 */  4, 4, 4, 4, 4, 4, 4, 4, 4,15, 4, 4, 4, 4, 4, 4,
                                        /* 0x10 */  4, 4, 4, 4, 4, 4, 4, 4, 4,15, 4, 4, 4, 4, 4, 4,
                                        /* 0x20 */  4,14,20,10, 8, 8,11, 4, 4,15,20,10, 8, 8,11, 4,
                                        /* 0x30 */  4, 4, 4, 4,23,23,19, 4, 4,15, 4, 4, 4, 4, 4, 4,
                                        /* 0x40 */  4, 4, 4, 4, 8, 8,19, 4, 4, 4, 4, 4, 8, 8,19, 4,
                                        /* 0x50 */  4, 4, 4, 4, 8, 8,19, 4, 4, 4, 4, 4, 8, 8,19, 4,
                                        /* 0x60 */  8, 8, 8, 8, 8, 8,19, 8, 8, 8, 8, 8, 8, 8,19, 8,
                                        /* 0x70 */ 19,19,19,19,19,19, 4,19, 4, 4, 4, 4, 8, 8,19, 4,
                                        /* 0x80 */  4, 4, 4, 4, 8, 8,19, 4, 4, 4, 4, 4, 8, 8,19, 4,
                                        /* 0x90 */  4, 4, 4, 4, 8, 8,19, 4, 4, 4, 4, 4, 8, 8,19, 4,
                                        /* 0xA0 */  4, 4, 4, 4, 8, 8,19, 4, 4, 4, 4, 4, 8, 8,19, 4,
                                        /* 0xB0 */  4, 4, 4, 4, 8, 8,19, 4, 4, 4, 4, 4, 8, 8,19, 4,
                                        /* 0xC0 */  4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 0, 4, 4, 4, 4,
                                        /* 0xD0 */  4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
                                        /* 0xE0 */  4,14, 4,23, 4,15, 4, 4, 4, 8, 4, 4, 4, 4, 4, 4,
                                        /* 0xF0 */  4, 4, 4, 4, 4, 4, 4, 4, 4,10, 4, 4, 4, 4, 4, 4};

    private final static short OP_INDEX_CB_STATES[] = {

                                        /*          0  1  2  3  4  5  6  7  8  9  A  B  C  D  E  F */
                                        /* 0x00 */ 00,00,00,00,00,00,23,00,00,00,00,00,00,00,23,00,
                                        /* 0x10 */ 00,00,00,00,00,00,23,00,00,00,00,00,00,00,23,00,
                                        /* 0x20 */ 00,00,00,00,00,00,23,00,00,00,00,00,00,00,23,00,
                                        /* 0x30 */ 00,00,00,00,00,00,23,00,00,00,00,00,00,00,23,00,
                                        /* 0x40 */ 00,00,00,00,00,00,20,00,00,00,00,00,00,00,20,00,
                                        /* 0x50 */ 00,00,00,00,00,00,20,00,00,00,00,00,00,00,20,00,
                                        /* 0x60 */ 00,00,00,00,00,00,20,00,00,00,00,00,00,00,20,00,
                                        /* 0x70 */ 00,00,00,00,00,00,20,00,00,00,00,00,00,00,20,00,
                                        /* 0x80 */ 00,00,00,00,00,00,23,00,00,00,00,00,00,00,23,00,
                                        /* 0x90 */ 00,00,00,00,00,00,23,00,00,00,00,00,00,00,23,00,
                                        /* 0xA0 */ 00,00,00,00,00,00,23,00,00,00,00,00,00,00,23,00,
                                        /* 0xB0 */ 00,00,00,00,00,00,23,00,00,00,00,00,00,00,23,00,
                                        /* 0xC0 */ 00,00,00,00,00,00,23,00,00,00,00,00,00,00,23,00,
                                        /* 0xD0 */ 00,00,00,00,00,00,23,00,00,00,00,00,00,00,23,00,
                                        /* 0xE0 */ 00,00,00,00,00,00,23,00,00,00,00,00,00,00,23,00,
                                        /* 0xF0 */ 00,00,00,00,00,00,23,00,00,00,00,00,00,00,23,00 };

    private final static short OP_ED_STATES[] = {

                                        /*          0  1  2  3  4  5  6  7  8  9  A  B  C  D  E  F */
                                        /* 0x00 */  8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
                                        /* 0x10 */  8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
                                        /* 0x20 */  8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
                                        /* 0x30 */  8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
                                        /* 0x40 */ 12,12,15,20, 8,14, 8, 9,12,12,15,20, 8,14, 8, 9,
                                        /* 0x50 */ 12,12,15,20, 8,14, 8, 9,12,12,15,20, 8,14, 8, 9,
                                        /* 0x60 */ 12,12,15,20, 8,14, 8,18,12,12,15,20, 8,14, 8,18,
                                        /* 0x70 */  8,12,15,20, 8,14, 8, 8,12,12,15,20, 8,14, 8, 8,
                                        /* 0x80 */  8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
                                        /* 0x90 */  8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
                                        /* 0xA0 */ 16,16,16,16, 8, 8, 8, 8,16,16,16,16, 8, 8, 8, 8,
                                        /* 0xB0 */ 16,16,16,16, 8, 8, 8, 8,16,16,16,16, 8, 8, 8, 8,
                                        /* 0xC0 */  8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
                                        /* 0xD0 */  8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
                                        /* 0xE0 */  8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
                                        /* 0xF0 */  8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8 };

    // --------------------------------------------------------------------------------------------
    // Precalculated tables for speed purposes
    // -------------------------------------------------------------------------------------------- 
    
    /** Pre-calculated result for DAA instruction */
    private static short[] DAA_TABLE;
    
    /** Sign, Zero table */
    private static short[] SZ_TABLE;
    
    /** Sign, Zero, Parity table */
    private static short[] SZP_TABLE;
    
    /** Flag lookup table for inc8 instruction */
    private static short[] SZHV_INC_TABLE;
    
    /** Flag lookup table for dec8 instruction */
    private static short[] SZHV_DEC_TABLE;
    
    /** Flag lookup table for add/adc instruction */
    private static short[] SZHVC_ADD_TABLE;
    
    /** Flag lookup table for dec/sbc instruction */
    private static short[] SZHVC_SUB_TABLE;
    
    /** Flag lookup table for bit instruction */
    private static short[] SZ_BIT_TABLE;
    
    // --------------------------------------------------------------------------------------------
    // Misc Helper Stuff
    // --------------------------------------------------------------------------------------------
    
    /** Easy bit reference for CB operations */
    private final static int
        BIT_0 = 0x01,
        BIT_1 = 0x02,
        BIT_2 = 0x04,
        BIT_3 = 0x08,
        BIT_4 = 0x10,
        BIT_5 = 0x20,
        BIT_6 = 0x40,
        BIT_7 = 0x80;
    
    //   --------------------------------------------------------------------------------------------


    /**
     *  Z80 Constructor.
     *
     *  @param p    Pointer to Z80's Ports
     */

    public Z80(Ports p)
    {
        this.port = p;
        
        // Generate flag lookups
        generateFlagTables();
        
        // Pre-calculate results for DAA instruction
        generateDAATable();
        
        // Generate memory arrays
        generateMemory();
    }


    /**
     *  Reset 
     *
     *  Note that some of these values aren't what a real Z80 would reset to.
     *  They are the values that the SMS BIOS (to the best of my knowledge)
     *  sets the registers to.
     *
     *  For example, the Index Registers should reset to 0xFFFF
     *  but doing so breaks 'Prince of Persia', so they are set to 0x0000.
     *
     *  The stack pointer is also reset to 0xDFF0 as opposed to 0x0000.
     */

    public final void reset()
    {
        a = a2 = 0;
        
        b = c = b2 = c2 = 0;
        d = e = d2 = e2 = 0;
        h = l = h2 = l2 = 0;
        ixL = ixH = 0;
        iyL = iyH = 0;
        
        r = 0;
        i = 0;
        f = 0; f2 = 0;

        pc = 0x0000;
        sp = 0xDFF0;
        tstates = 0;
        totalCycles = 0;

        im = 0;
        iff1 = false;
        iff2 = false;
        EI_inst = false;
        interruptVector = 0;
        halt = false;
    }

    /**
     *  Return Next Opcode for Debugging Purposes
     *
     *  @return             String containing opcode bytes
     */

    /*public final String getOp()
    {
        int opcode = readMem(pc);
        String oplist = Integer.toHexString(opcode&0xff);

        switch(opcode)
        {
            case 0xCB:
            case 0xED: opcode = readMem(pc+1); oplist += " "+Integer.toHexString(opcode&0xff); break;
            case 0xDD:
            case 0xFD:
                opcode = readMem(pc+1);
                oplist += " "+Integer.toHexString(opcode&0xff);
                if (opcode == 0xCB) // DDCB etc
                {
                    opcode = readMem(pc+3);
                    oplist += " "+Integer.toHexString(opcode&0xff);
                }
                break;
            default:
                break;
        }

        return oplist.toUpperCase();
    }*/
    
    /**
     *  Return Mnemonic of next opcode for Debugging Purposes
     *
     *  @return             String containing opcode bytes
     */

    /*public final String getMnu()
    {
        int opcode = readMem(pc);
        Mnemonic mnu = new Mnemonic();
        switch(opcode)
        {
            // special cases
            case 0xDD:
            case 0xFD:
                opcode = readMem(pc+1); return mnu.getIndex(opcode);
            case 0xCB:
                opcode = readMem(pc+1); return mnu.getCB(opcode);
            case 0xED:
                opcode = readMem(pc+1); return mnu.getED(opcode);
            default:
                return mnu.getOP(opcode);
        }
        return null;
    }*/


    /**
     *  Output Contents of Z80 Registers to Console for Debugging Purposes
     */

    /*private final void consoledebug()
    {
        System.out.println("----------------------------------------------------------------------------");
        System.out.println(Integer.toHexString(pc)+" 0x"+getOp()+" "+getMnu());
        System.out.println("A : "+Integer.toHexString(a)+" BC : "+Integer.toHexString(getBC())+" DE : "+Integer.toHexString(getDE())+" HL : "+Integer.toHexString(getHL())+
                           " IX: "+Integer.toHexString(getIX())+  " IY: "+Integer.toHexString(getIY()));

        exAF(); exBC(); exDE(); exHL();

        System.out.println("A': "+Integer.toHexString(a)+" BC': "+Integer.toHexString(getBC())+" DE': "+Integer.toHexString(getDE())+ " HL': "+Integer.toHexString(getHL())+ " SP: "+Integer.toHexString(sp));

        exAF(); exBC(); exDE(); exHL();

        //System.out.println("FS: "+getSign()+" FZ: "+getZero()+" FHC: "+getHc()+" FP: "+getParity()+" FN: "+getNegative()+ " FC: "+getCarry());
    }*/


    /**
     *  Run Z80
     *
     *  @param cycles       Machine cycles to run for in total
     *  @param cycles
     *
     */

    public final void run(int cycles, int cyclesTo)
    {
        tstates += cycles;
        
        if (cycles != 0) 
            totalCycles = cycles;
                
        if (!Setup.ACCURATE_INTERRUPT_EMULATION)
        {
            if (interruptLine)
                interrupt();                    // Check for interrupt
        }
        
        while (tstates > cyclesTo)
        {
            if (Setup.ACCURATE_INTERRUPT_EMULATION)
            {
                if (interruptLine)
                    interrupt();                    // Check for interrupt
            }
            
            // ------------------------------------------------------------------------------------
            // Fetch & Interpret Opcodes
            // Main Opcode Switch Rolled In For Speed
            // ------------------------------------------------------------------------------------
            int opcode = readMem(pc++);                    // Fetch & Interpret Opcode     
            
            if (Setup.ACCURATE_INTERRUPT_EMULATION)
                EI_inst = false;
            
            tstates -= OP_STATES[opcode];   // Decrement TStates
            
            if (Setup.REFRESH_EMULATION)
                incR();

            switch (opcode)
            {
                case 0x00: break;                                                   // NOP
                case 0x01: c = readMem(pc++); b = readMem(pc++); break;             // LD BC,nn
                case 0x02: writeMem(getBC(), a); break;                             // LD (BC),A
                case 0x03: incBC(); break;                                          // INC BC
                case 0x04: b = inc8(b); break;                                      // INC B
                case 0x05: b = dec8(b); break;                                      // DEC B
                case 0x06: b = readMem(pc++); break;                                // LD B,n
                case 0x07: rlca_a(); break;                                         // RLCA
                case 0x08: exAF(); break;                                           // EX AF AF'
                case 0x09: setHL(add16(getHL(), getBC())); break;                   // ADD HL,BC
                case 0x0A: a = readMem(getBC()); break;                             // LD A,(BC)
                case 0x0B: decBC(); break;                                          // DEC BC
                case 0x0C: c = inc8(c); break;                                      // INC C
                case 0x0D: c = dec8(c); break;                                      // DEC C
                case 0x0E: c = readMem(pc++); break;                                // LD C,n
                case 0x0F: rrca_a(); break;                                         // RRCA
                case 0x10: b = (b - 1) & 0xff;                                      // DJNZ (PC+e)
                           jr(b != 0); break;
                case 0x11: e = readMem(pc++); d = readMem(pc++); break;             // LD DE,nn
                case 0x12: writeMem(getDE(), a); break;                             // LD (DE), A
                case 0x13: incDE(); break;                                          // INC DE
                case 0x14: d = inc8(d); break;                                      // INC D
                case 0x15: d = dec8(d); break;                                      // DEC D
                case 0x16: d = (readMem(pc++)); break;                              // LD D,n
                case 0x17: rla_a(); break;                                          // RLA
                case 0x18: pc+= d() +1; break;                                      // JR (PC+e)
                case 0x19: setHL(add16(getHL(), getDE())); break;                   // ADD HL,DE
                case 0x1A: a = readMem(getDE()); break;                             // LD A,(DE)
                case 0x1B: decDE(); break;                                          // DEC DE
                case 0x1C: e = inc8(e); break;                                      // INC E
                case 0x1D: e = dec8(e); break;                                      // DEC E
                case 0x1E: e = readMem(pc++); break;                                // LD E,N
                case 0x1F: rra_a(); break;                                          // RRA
                case 0x20: jr(!((f & F_ZERO) != 0)); break;                         // JR NZ,(PC+e)
                case 0x21: l = readMem(pc++); h = readMem(pc++); break;             // LD HL,nn
                case 0x22: {                                                        // LD (nn),HL
                                int location = readMemWord(pc);
                                writeMem(location, l);
                                writeMem(++location, h);
                                pc+=2;
                            }
                            break;
                case 0x23: incHL(); break;                                          // INC HL
                case 0x24: h = inc8(h); break;                                      // INC H
                case 0x25: h = dec8(h); break;                                      // DEC H
                case 0x26: h = readMem(pc++); break;                                // LD H,n
                case 0x27: daa(); break;                                            // DAA
                case 0x28: jr(((f & F_ZERO) != 0)); break;                          // JR Z,(PC+e)
                case 0x29: setHL(add16(getHL(), getHL())); break;                   // ADD HL,HL
                case 0x2A:  {
                                int location = readMemWord(pc);
                                l = readMem(location);
                                h = readMem(location+1);
                                pc+=2;          
                            }
                            break;
                case 0x2B: decHL(); break;                                          // DEC HL
                case 0x2C: l = inc8(l); break;                                      // INC L
                case 0x2D: l = dec8(l); break;                                      // DEC L
                case 0x2E: l = readMem(pc++); break;                                // LD L,n
                case 0x2F: cpl_a(); break;                                          // CPL
                case 0x30: jr(!((f & F_CARRY) != 0)); break;                        // JR NC,(PC+e)
                case 0x31: sp = readMemWord(pc); pc+=2; break;                      // LD SP,nn
                case 0x32: writeMem(readMemWord(pc), a); pc+=2; break;              // LD (nn),A
                case 0x33: sp++; break;                                             // INC SP
                case 0x34: incMem(getHL()); break;                                  // INC (HL)
                case 0x35: decMem(getHL()); break;                                  // DEC (HL)
                case 0x36: writeMem(getHL(), readMem(pc++)); break;                 // LD (HL),n
                case 0x37: f |= F_CARRY; f &=~ F_NEGATIVE; f &=~ F_HALFCARRY;       // SCF
                           break;
                case 0x38: jr((f & F_CARRY) != 0); break;                           // JR C,(PC+e)
                case 0x39: setHL(add16(getHL(), sp)); break;                        // ADD HL,SP
                case 0x3A: a = readMem(readMemWord(pc)); pc+=2; break;              // LD A,(nn)
                case 0x3B: sp--; break;                                             // DEC SP
                case 0x3C: a = inc8(a); break;                                      // INC A
                case 0x3D: a = dec8(a); break;                                      // DEC A
                case 0x3E: a = readMem(pc++); break;                                // LD A,n
                case 0x3F: ccf(); break;                                            // CCF
                case 0x40: break;                                                   // LD B,B
                case 0x41: b = c; break;                                            // LD B,C
                case 0x42: b = d; break;                                            // LD B,D
                case 0x43: b = e; break;                                            // LD B,E
                case 0x44: b = h; break;                                            // LD B,H
                case 0x45: b = l; break;                                            // LD B,L
                case 0x46: b = readMem(getHL()); break;                             // LD B,(HL)
                case 0x47: b = a; break;                                            // LD B,A
                case 0x48: c = b; break;                                            // LD C,B
                case 0x49: break;                                                   // LD C,C
                case 0x4A: c = d; break;                                            // LD C,D
                case 0x4B: c = e; break;                                            // LD C,E
                case 0x4C: c = h; break;                                            // LD C,H
                case 0x4D: c = l; break;                                            // LD C,L
                case 0x4E: c = readMem(getHL()); break;                             // LD C,(HL)
                case 0x4F: c = a; break;                                            // LD C,A
                case 0x50: d = b; break;                                            // LD D,B
                case 0x51: d = c; break;                                            // LD D,C
                case 0x52: break;                                                   // LD D,D
                case 0x53: d = e; break;                                            // LD D,E
                case 0x54: d = h; break;                                            // LD D,H
                case 0x55: d = l; break;                                            // LD D,L
                case 0x56: d = readMem(getHL()); break;                             // LD D,(HL)
                case 0x57: d = a; break;                                            // LD D,A
                case 0x58: e = b; break;                                            // LD E,B
                case 0x59: e = c; break;                                            // LD E,C
                case 0x5A: e = d; break;                                            // LD E,D
                case 0x5B: break;                                                   // LD E,E
                case 0x5C: e = h; break;                                            // LD E,H
                case 0x5D: e = l; break;                                            // LD E,L
                case 0x5E: e = readMem(getHL()); break;                             // LD E,(HL)
                case 0x5F: e = a; break;                                            // LD E,A
                case 0x60: h = b; break;                                            // LD H,B
                case 0x61: h = c; break;                                            // LD H,C
                case 0x62: h = d; break;                                            // LD H,D
                case 0x63: h = e; break;                                            // LD H,E
                case 0x64: break;                                                   // LD H,H
                case 0x65: h = l; break;                                            // LD H,L
                case 0x66: h = readMem(getHL()); break;                             // LD H,(HL)
                case 0x67: h = a; break;                                            // LD H,A
                case 0x68: l = b; break;                                            // LD L,B
                case 0x69: l = c; break;                                            // LD L,C
                case 0x6A: l = d; break;                                            // LD L,D
                case 0x6B: l = e; break;                                            // LD L,E
                case 0x6C: l = h; break;                                            // LD L,H
                case 0x6D: break;                                                   // LD L,L
                case 0x6E: l = readMem(getHL()); break;                             // LD L,(HL)
                case 0x6F: l = a; break;                                            // LD L,A
                case 0x70: writeMem(getHL(), b); break;                             // LD (HL),B
                case 0x71: writeMem(getHL(), c); break;                             // LD (HL),C
                case 0x72: writeMem(getHL(), d); break;                             // LD (HL),D
                case 0x73: writeMem(getHL(), e); break;                             // LD (HL),E
                case 0x74: writeMem(getHL(), h); break;                             // LD (HL),H
                case 0x75: writeMem(getHL(), l); break;                             // LD (HL),L
                case 0x76: if (HALT_SPEEDUP) tstates = 0;
                           halt = true; pc--; return;                               // HALT
                case 0x77: writeMem(getHL(), a); break;                             // LD (HL),A
                case 0x78: a = b; break;                                            // LD A,B
                case 0x79: a = c; break;                                            // LD A,C
                case 0x7A: a = d; break;                                            // LD A,D
                case 0x7B: a = e; break;                                            // LD A,E
                case 0x7C: a = h; break;                                            // LD A,H
                case 0x7D: a = l; break;                                            // LD A,L
                case 0x7E: a = readMem(getHL()); break;                             // LD A,(HL)
                case 0x7F: break;                                                   // LD A,A
                case 0x80: add_a(b); break;                                         // ADD A,B
                case 0x81: add_a(c); break;                                         // ADD A,C
                case 0x82: add_a(d); break;                                         // ADD A,D
                case 0x83: add_a(e); break;                                         // ADD A,E
                case 0x84: add_a(h); break;                                         // ADD A,H
                case 0x85: add_a(l); break;                                         // ADD A,L
                case 0x86: add_a(readMem(getHL())); break;                          // ADD A,(HL)
                case 0x87: add_a(a); break;                                         // ADD A,A
                case 0x88: adc_a(b); break;                                         // ADC A,B
                case 0x89: adc_a(c); break;                                         // ADC A,C
                case 0x8A: adc_a(d); break;                                         // ADC A,D
                case 0x8B: adc_a(e); break;                                         // ADC A,E
                case 0x8C: adc_a(h); break;                                         // ADC A,H
                case 0x8D: adc_a(l); break;                                         // ADC A,L
                case 0x8E: adc_a(readMem(getHL())); break;                          // ADC A,(HL)
                case 0x8F: adc_a(a); break;                                         // ADC A,A
                case 0x90: sub_a(b); break;                                         // SUB A,B
                case 0x91: sub_a(c); break;                                         // SUB A,C
                case 0x92: sub_a(d); break;                                         // SUB A,D
                case 0x93: sub_a(e); break;                                         // SUB A,E
                case 0x94: sub_a(h); break;                                         // SUB A,H
                case 0x95: sub_a(l); break;                                         // SUB A,L
                case 0x96: sub_a(readMem(getHL())); break;                          // SUB A,(HL)
                case 0x97: sub_a(a); break;                                         // SUB A,A
                case 0x98: sbc_a(b); break;                                         // SBC A,B
                case 0x99: sbc_a(c); break;                                         // SBC A,C
                case 0x9A: sbc_a(d); break;                                         // SBC A,D
                case 0x9B: sbc_a(e); break;                                         // SBC A,E
                case 0x9C: sbc_a(h); break;                                         // SBC A,H
                case 0x9D: sbc_a(l); break;                                         // SBC A,L
                case 0x9E: sbc_a(readMem(getHL())); break;                          // SBC A,(HL)
                case 0x9F: sbc_a(a); break;                                         // SBC A,A
                case 0xA0: f = SZP_TABLE[a &= b] | F_HALFCARRY; break;              // AND A,B
                case 0xA1: f = SZP_TABLE[a &= c] | F_HALFCARRY; break;              // AND A,C
                case 0xA2: f = SZP_TABLE[a &= d] | F_HALFCARRY; break;              // AND A,D
                case 0xA3: f = SZP_TABLE[a &= e] | F_HALFCARRY; break;              // AND A,E
                case 0xA4: f = SZP_TABLE[a &= h] | F_HALFCARRY; break;              // AND A,H
                case 0xA5: f = SZP_TABLE[a &= l] | F_HALFCARRY; break;              // AND A,L
                case 0xA6: f = SZP_TABLE[a &= readMem(getHL())] | F_HALFCARRY;break;// AND A,(HL)
                case 0xA7: f = SZP_TABLE[a] | F_HALFCARRY; break;                   // AND A,A
                case 0xA8: f = SZP_TABLE[a ^= b]; break;                            // XOR A,B
                case 0xA9: f = SZP_TABLE[a ^= c]; break;                            // XOR A,C
                case 0xAA: f = SZP_TABLE[a ^= d]; break;                            // XOR A,D
                case 0xAB: f = SZP_TABLE[a ^= e]; break;                            // XOR A,E
                case 0xAC: f = SZP_TABLE[a ^= h]; break;                            // XOR A,H
                case 0xAD: f = SZP_TABLE[a ^= l]; break;                            // XOR A,L
                case 0xAE: f = SZP_TABLE[a ^= readMem(getHL())]; break;             // XOR A,(HL)
                case 0xAF: f = SZP_TABLE[a = 0]; break;                             // XOR A,A (=0)
                case 0xB0: f = SZP_TABLE[a |= b]; break;                            // OR A,B
                case 0xB1: f = SZP_TABLE[a |= c]; break;                            // OR A,C
                case 0xB2: f = SZP_TABLE[a |= d]; break;                            // OR A,D
                case 0xB3: f = SZP_TABLE[a |= e]; break;                            // OR A,E
                case 0xB4: f = SZP_TABLE[a |= h]; break;                            // OR A,H
                case 0xB5: f = SZP_TABLE[a |= l]; break;                            // OR A,L
                case 0xB6: f = SZP_TABLE[a |= readMem(getHL())]; break;             // OR A,(HL)
                case 0xB7: f = SZP_TABLE[a]; break;                                 // OR A,A
                case 0xB8: cp_a(b); break;                                          // CP A,B
                case 0xB9: cp_a(c); break;                                          // CP A,C
                case 0xBA: cp_a(d); break;                                          // CP A,D
                case 0xBB: cp_a(e); break;                                          // CP A,E
                case 0xBC: cp_a(h); break;                                          // CP A,H
                case 0xBD: cp_a(l); break;                                          // CP A,L
                case 0xBE: cp_a(readMem(getHL())); break;                           // CP A,(HL)
                case 0xBF: cp_a(a); break;                                          // CP A,A
                case 0xC0: ret((f & F_ZERO) == 0);  break;                          // RET NZ
                case 0xC1: setBC(readMemWord(sp)); sp+=2; break;                    // POP BC
                case 0xC2: jp((f & F_ZERO) == 0); break;                            // JP NZ,(nn)
                case 0xC3: pc = readMemWord(pc); break;                             // JP (nn)
                case 0xC4: call((f & F_ZERO) == 0); break;                          // CALL NZ (nn)
                case 0xC5: push(b, c); break;                                       // PUSH BC
                case 0xC6: add_a(readMem(pc++)); break;                             // ADD A,n
                case 0xC7: push(pc); pc=0x00; break;                                // RST 00H
                case 0xC8: ret((f & F_ZERO) != 0); break;                           // RET Z
                case 0xC9: pc = readMemWord(sp); sp+=2; break;                      // RET
                case 0xCA: jp((f & F_ZERO) != 0); break;                            // JP Z,(nn)
                case 0xCB: doCB(readMem(pc++)); break;                              // CB Opcode
                case 0xCC: call((f & F_ZERO) != 0); break;                          // CALL Z (nn)
                case 0xCD: push(pc+2); pc = readMemWord(pc); break;                 // CALL (nn)
                case 0xCE: adc_a(readMem(pc++)); break;                             // ADC A,n
                case 0xCF: push(pc); pc=0x08; break;                                // RST 08H
                case 0xD0: ret((f & F_CARRY) == 0); break;                          // RET NC
                case 0xD1: setDE(readMemWord(sp)); sp+=2; break;                    // POP DE
                case 0xD2: jp((f & F_CARRY) == 0); break;                           // JP NC,(nn)
                case 0xD3: port.out(readMem(pc++), a); break;                       // OUT (n),A
                case 0xD4: call((f & F_CARRY) == 0); break;                         // CALL NC (nn)
                case 0xD5: push(d, e); break;                                       // PUSH DE
                case 0xD6: sub_a(readMem(pc++)); break;                             // SUB n
                case 0xD7: push(pc); pc=0x10; break;                                // RST 10H
                case 0xD8: ret(((f & F_CARRY) != 0)); break;                        // RET C
                case 0xD9: exBC(); exDE(); exHL(); break;                           // EXX
                case 0xDA: jp((f & F_CARRY) != 0); break;                           // JP C,(nn)
                case 0xDB: a = port.in(readMem(pc++)); break;                       // IN A,(n)
                case 0xDC: call((f & F_CARRY) != 0); break;                         // CALL C (nn)
                case 0xDD: doIndexOpIX(readMem(pc++)); break;                       // DD Opcode
                case 0xDE: sbc_a(readMem(pc++)); break;                             // SBC A,n
                case 0xDF: push(pc); pc=0x18; break;                                // RST 18H
                case 0xE0: ret((f & F_PARITY) == 0);  break;                        // RET PO
                case 0xE1: setHL(readMemWord(sp)); sp+=2; break;                    // POP HL
                case 0xE2: jp((f & F_PARITY) == 0); break;                          // JP PO,(nn)
                case 0xE3:  {                                                       // EX (SP),HL
                                int temp = h;
                                h = readMem(sp+1);
                                writeMem(sp+1, temp);
                                
                                temp = l;
                                l = readMem(sp);
                                writeMem(sp, temp); 
                            } break;
                case 0xE4: call((f & F_PARITY) == 0); break;                        // CALL PO (nn)
                case 0xE5: push(h, l); break;                                       // PUSH HL
                case 0xE6: f = SZP_TABLE[a &= readMem(pc++)] | F_HALFCARRY; break;  // AND (n)
                case 0xE7: push(pc); pc=0x20; break;                                // RST 20H
                case 0xE8: ret((f & F_PARITY) != 0); break;                         // RET PE
                case 0xE9: pc = getHL(); break;                                     // JP (HL)
                case 0xEA: jp((f & F_PARITY) != 0); break;                          // JP PE,(nn)
                case 0xEB:  {                                                       // EX DE,HL
                                int temp = d;
                                d = h;
                                h = temp;
                                temp = e;
                                e = l;
                                l = temp;   
                            } break;
                case 0xEC: call((f & F_PARITY) != 0); break;                        // CALL PE (nn)
                case 0xED: doED(readMem(pc)); break;                                // ED Opcode
                case 0xEE: f = SZP_TABLE[a ^= readMem(pc++)]; break;                // XOR n
                case 0xEF: push(pc); pc=0x28; break;                                // RST 28H
                case 0xF0: ret((f & F_SIGN) == 0); break;                           // RET P
                case 0xF1: f = readMem(sp++); a = readMem(sp++); break;             // POP AF                                               
                case 0xF2: jp((f & F_SIGN) == 0); break;                            // JP P,(nn)
                case 0xF3: iff1 = iff2 = false; EI_inst = true; break;              // DI
                case 0xF4: call((f & F_SIGN) == 0);  break;                         // CALL P (nn)
                case 0xF5: push(a, f); break;                                       // PUSH AF
                case 0xF6: f = SZP_TABLE[a |= readMem(pc++)]; break;                // OR n
                case 0xF7: push(pc); pc=0x30; break;                                // RST 30H
                case 0xF8: ret((f & F_SIGN) != 0); break;                           // RET M
                case 0xF9: sp = getHL(); break;                                     // LD SP,HL
                case 0xFA: jp((f & F_SIGN) != 0); break;                            // JP M,(nn)
                case 0xFB: iff1 = iff2 = EI_inst = true; break;                     // EI
                case 0xFC: call((f & F_SIGN) != 0); break;                          // CALL M (nn)
                case 0xFD: doIndexOpIY(readMem(pc++)); break;                       // FD Opcode
                case 0xFE: cp_a(readMem(pc++)); break;                              // CP n
                case 0xFF: push(pc); pc=0x38; break;                                // RST 38H
                
            } // end switch
            
        }
    }
    
    /**
     * Get current cycle number
     * 
     * @return  Cycle number
     */
    
    public static int getCycle()
    {
        return totalCycles - tstates;
    }

    /**
     *  Generate Non Maskable Interrupt (NMI)
     */

    public final void nmi()
    {
        iff2 = iff1;
        iff1 = false;

        if (Setup.REFRESH_EMULATION)
            incR();
        
        // If we're in a halt instruction, increment the PC and get out of it
        if (halt)
        {
            pc++;
            halt = false;
        }

        push(pc);                // Preserve PC on stack
        pc = 0x66;
        tstates -= 11;
    }


    /**
     *  Normal Interrupt Routine
     */

    private final void interrupt()
    {
        // Interrupts not allowed OR
        // Intterupts not allowed after EI instruction
        if (!iff1 || (Setup.ACCURATE_INTERRUPT_EMULATION && EI_inst)) return;
        
        // If we're in a halt instruction, increment the PC and get out of it
        if (halt)
        {
            pc++;
            halt = false;
        }

        if (Setup.REFRESH_EMULATION)
            incR();
        
        iff1 = iff2 = false;
        interruptLine = false;
        
        push(pc);                // Preserve PC on stack
        
        // IM 0: Execute Instruction on Bus
        if (im == 0)
        {
            pc = (interruptVector == 0 || interruptVector == 0xFF) ? 0x38 : interruptVector;
            tstates -= 13;
        }
        // IM 1: Do RST 38h. Ignore Value on Bus.
        else if (im == 1)
        {
            pc = 0x38;
            tstates -= 13;
        }
        // IM 2
        else
        {
            pc = readMemWord((i << 8) + interruptVector);
            tstates -= 19;
        }
    }

    /**
     *  Jump
     *
     *  @param condition        If true jump will be taken
     */

    private final void jp(boolean condition)
    {
        if (condition) pc = readMemWord(pc);
        else pc += 2;
    }


    /**
     *  Jump Relative
     *
     *  @param condition        If true jump will be taken
     */

    private final void jr(boolean condition)
    {
        if (condition)
        {
            pc += d() + 1;
            tstates-=5;
        }
        else pc++;
    }


    /**
     *  Call
     *
     *  @param condition        If true call will be taken
     */

    private final void call(boolean condition)
    {
        if (condition)
        {
            push(pc+2);                 // write value of PC to stack
            pc = readMemWord(pc);
            tstates-=7;
        }
        else pc += 2;
    }


    /**
     *  Return
     *
     *  @param condition        If true return will be taken
     */

    private final void ret(boolean condition)
    {
        if (condition)
        {
            pc = readMemWord(sp);
            sp+=2;
            tstates-=6;
        }
    }


    /**
     *  Push Value Onto Stack
     *
     *  @param value        Value to push
     */

    private final void push(int value)
    {
        writeMem(--sp, value >> 8);     // (SP - 1) <- high
        writeMem(--sp, value & 0xff);   // (SP - 2) <- low
    }
    
    private final void push(int h, int l)
    {
        writeMem(--sp, h); // (SP - 1) <- high
        writeMem(--sp, l); // (SP - 2) <- low
    }


    /**
     *  INC - Increment Memory Location
     *
     *  @param offset       Memory Offset to Increment
     */

    private final void incMem(int offset)
    {
        writeMem(offset, inc8(readMem(offset)));
    }


    /**
     *  DEC - Decrement Memory Location
     *
     *  @param offset       Memory Offset to Increment
     */

    private final void decMem(int offset)
    {
        writeMem(offset, dec8(readMem(offset)));
    }

    /**
     *  CCF - Complement Carry Flag
     */

    private final void ccf()
    {
        if ((f & F_CARRY) != 0)
        {
            f &=~ F_CARRY;
            f |= F_HALFCARRY;
        }
        else
        {
            f |= F_CARRY;
            f &=~ F_HALFCARRY;
        }
        f &=~ F_NEGATIVE;
    }


    /**
     *  DAA - Decimal Adjust Accumulator
     *  adds 6 to left and/or right nibble
     *
     *  Pre-Calculated Result For Speed
     *  
     *  Checked with ZEXALL
     */

    private final void daa()
    {
        // Get result for calculated table (carry flag = bit 8, negative = bit 9, halfcarry = bit 10)
        int temp = DAA_TABLE[a | ((f & F_CARRY) << 8) | ((f & F_NEGATIVE) << 8) | ((f & F_HALFCARRY) << 6)];    
        a = temp & 0xFF;
        f = (f & F_NEGATIVE) | (temp >> 8);
    }


    /**
     *  Execute CB Prefixed Opcode
     *
     *  @param opcode       Opcode hex value
     */

    private final void doCB(int opcode)
    {
        if (Setup.REFRESH_EMULATION)
            incR();
        
        tstates -= OP_CB_STATES[opcode];

        switch(opcode)
        {
            case 0x00: b = (rlc(b)); break;                                 // RLC B
            case 0x01: c = (rlc(c)); break;                                 // RLC C
            case 0x02: d = (rlc(d)); break;                                 // RLC D
            case 0x03: e = (rlc(e)); break;                                 // RLC E
            case 0x04: h = (rlc(h)); break;                                 // RLC H
            case 0x05: l = (rlc(l)); break;                                 // RLC L
            case 0x06: writeMem(getHL(), rlc(readMem(getHL()))); break;     // RLC (HL)
            case 0x07: a = rlc(a); break;                                   // RLC A
            case 0x08: b = (rrc(b)); break;                                 // RRC B
            case 0x09: c = (rrc(c)); break;                                 // RRC C
            case 0x0A: d = (rrc(d)); break;                                 // RRC D
            case 0x0B: e = (rrc(e)); break;                                 // RRC E
            case 0x0C: h = (rrc(h)); break;                                 // RRC H
            case 0x0D: l = (rrc(l)); break;                                 // RRC L
            case 0x0E: writeMem(getHL(), rrc(readMem(getHL()))); break;     // RRC (HL)
            case 0x0F: a = rrc(a); break;                                   // RRC A
            case 0x10: b = (rl(b)); break;                                  // RL B
            case 0x11: c = (rl(c)); break;                                  // RL C
            case 0x12: d = (rl(d)); break;                                  // RL D
            case 0x13: e = (rl(e)); break;                                  // RL E
            case 0x14: h = (rl(h)); break;                                  // RL H
            case 0x15: l = (rl(l)); break;                                  // RL L
            case 0x16: writeMem(getHL(), rl(readMem(getHL()))); break;      // RL (HL)
            case 0x17: a = rl(a); break;                                    // RL A
            case 0x18: b = (rr(b)); break;                                  // RR B
            case 0x19: c = (rr(c)); break;                                  // RR C
            case 0x1A: d = (rr(d)); break;                                  // RR D
            case 0x1B: e = (rr(e)); break;                                  // RR E
            case 0x1C: h = (rr(h)); break;                                  // RR H
            case 0x1D: l = (rr(l)); break;                                  // RR L
            case 0x1E: writeMem(getHL(), rr(readMem(getHL()))); break;      // RR (HL)
            case 0x1F: a = rr(a); break;                                    // RR A
            case 0x20: b = (sla(b)); break;                                 // SLA B
            case 0x21: c = (sla(c)); break;                                 // SLA C
            case 0x22: d = (sla(d)); break;                                 // SLA D
            case 0x23: e = (sla(e)); break;                                 // SLA E
            case 0x24: h = (sla(h)); break;                                 // SLA H
            case 0x25: l = (sla(l)); break;                                 // SLA L
            case 0x26: writeMem(getHL(), sla(readMem(getHL()))); break;     // SLA (HL)
            case 0x27: a = sla(a); break;                                   // SLA A
            case 0x28: b = (sra(b)); break;                                 // SRA B
            case 0x29: c = (sra(c)); break;                                 // SRA C
            case 0x2A: d = (sra(d)); break;                                 // SRA D
            case 0x2B: e = (sra(e)); break;                                 // SRA E
            case 0x2C: h = (sra(h)); break;                                 // SRA H
            case 0x2D: l = (sra(l)); break;                                 // SRA L
            case 0x2E: writeMem(getHL(), sra(readMem(getHL()))); break;     // SRA (HL)
            case 0x2F: a = sra(a); break;                                   // SRA A
            case 0x30: b = (sll(b)); break;                                 // SLL B
            case 0x31: c = (sll(c)); break;                                 // SLL C
            case 0x32: d = (sll(d)); break;                                 // SLL D
            case 0x33: e = (sll(e)); break;                                 // SLL E
            case 0x34: h = (sll(h)); break;                                 // SLL H
            case 0x35: l = (sll(l)); break;                                 // SLL L
            case 0x36: writeMem(getHL(), sll(readMem(getHL()))); break;     // SLL (HL) 
            case 0x37: a = (sll(a)); break;                                 // SLL A
            case 0x38: b = (srl(b)); break;                                 // SRL B
            case 0x39: c = (srl(c)); break;                                 // SRL C
            case 0x3A: d = (srl(d)); break;                                 // SRL D
            case 0x3B: e = (srl(e)); break;                                 // SRL E
            case 0x3C: h = (srl(h)); break;                                 // SRL H
            case 0x3D: l = (srl(l)); break;                                 // SRL L
            case 0x3E: writeMem(getHL(), srl(readMem(getHL()))); break;     // SRL (HL)
            case 0x3F: a = srl(a); break;                                   // SRL A
            case 0x40: bit(b & BIT_0); break;                               // BIT 0,B
            case 0x41: bit(c & BIT_0); break;                               // BIT 0,C
            case 0x42: bit(d & BIT_0); break;                               // BIT 0,D
            case 0x43: bit(e & BIT_0); break;                               // BIT 0,E
            case 0x44: bit(h & BIT_0); break;                               // BIT 0,H
            case 0x45: bit(l & BIT_0); break;                               // BIT 0,L
            case 0x46: bit(readMem(getHL()) & BIT_0); break;                // BIT 0,(HL)
            case 0x47: bit(a & BIT_0); break;                               // BIT 0,A
            case 0x48: bit(b & BIT_1); break;                               // BIT 1,B
            case 0x49: bit(c & BIT_1); break;                               // BIT 1,C
            case 0x4A: bit(d & BIT_1); break;                               // BIT 1,D
            case 0x4B: bit(e & BIT_1); break;                               // BIT 1,E
            case 0x4C: bit(h & BIT_1); break;                               // BIT 1,H
            case 0x4D: bit(l & BIT_1); break;                               // BIT 1,L
            case 0x4E: bit(readMem(getHL()) & BIT_1); break;                // BIT 1,(HL)
            case 0x4F: bit(a & BIT_1); break;                               // BIT 1,A
            case 0x50: bit(b & BIT_2); break;                               // BIT 2,B
            case 0x51: bit(c & BIT_2); break;                               // BIT 2,C
            case 0x52: bit(d & BIT_2); break;                               // BIT 2,D
            case 0x53: bit(e & BIT_2); break;                               // BIT 2,E
            case 0x54: bit(h & BIT_2); break;                               // BIT 2,H
            case 0x55: bit(l & BIT_2); break;                               // BIT 2,L
            case 0x56: bit(readMem(getHL()) & BIT_2); break;                // BIT 2,(HL)
            case 0x57: bit(a & BIT_2); break;                               // BIT 2,A
            case 0x58: bit(b & BIT_3); break;                               // BIT 3,B
            case 0x59: bit(c & BIT_3); break;                               // BIT 3,C
            case 0x5A: bit(d & BIT_3); break;                               // BIT 3,D
            case 0x5B: bit(e & BIT_3); break;                               // BIT 3,E
            case 0x5C: bit(h & BIT_3); break;                               // BIT 3,H
            case 0x5D: bit(l & BIT_3); break;                               // BIT 3,L
            case 0x5E: bit(readMem(getHL()) & BIT_3); break;                // BIT 3,(HL)
            case 0x5F: bit(a & BIT_3); break;                               // BIT 3,A
            case 0x60: bit(b & BIT_4); break;                               // BIT 4,B
            case 0x61: bit(c & BIT_4); break;                               // BIT 4,C
            case 0x62: bit(d & BIT_4); break;                               // BIT 4,D
            case 0x63: bit(e & BIT_4); break;                               // BIT 4,E
            case 0x64: bit(h & BIT_4); break;                               // BIT 4,H
            case 0x65: bit(l & BIT_4); break;                               // BIT 4,L
            case 0x66: bit(readMem(getHL()) & BIT_4); break;                // BIT 4,(HL)
            case 0x67: bit(a & BIT_4); break;                               // BIT 4,A
            case 0x68: bit(b & BIT_5); break;                               // BIT 5,B
            case 0x69: bit(c & BIT_5); break;                               // BIT 5,C
            case 0x6A: bit(d & BIT_5); break;                               // BIT 5,D
            case 0x6B: bit(e & BIT_5); break;                               // BIT 5,E
            case 0x6C: bit(h & BIT_5); break;                               // BIT 5,H
            case 0x6D: bit(l & BIT_5); break;                               // BIT 5,L
            case 0x6E: bit(readMem(getHL()) & BIT_5); break;                // BIT 5,(HL)
            case 0x6F: bit(a & BIT_5); break;                               // BIT 5,A
            case 0x70: bit(b & BIT_6); break;                               // BIT 6,B
            case 0x71: bit(c & BIT_6); break;                               // BIT 6,C
            case 0x72: bit(d & BIT_6); break;                               // BIT 6,D
            case 0x73: bit(e & BIT_6); break;                               // BIT 6,E
            case 0x74: bit(h & BIT_6); break;                               // BIT 6,H
            case 0x75: bit(l & BIT_6); break;                               // BIT 6,L
            case 0x76: bit(readMem(getHL()) & BIT_6); break;                // BIT 6,(HL)
            case 0x77: bit(a & BIT_6); break;                               // BIT 6,A
            case 0x78: bit(b & BIT_7); break;                               // BIT 7,B
            case 0x79: bit(c & BIT_7); break;                               // BIT 7,C
            case 0x7A: bit(d & BIT_7); break;                               // BIT 7,D
            case 0x7B: bit(e & BIT_7); break;                               // BIT 7,E
            case 0x7C: bit(h & BIT_7); break;                               // BIT 7,H
            case 0x7D: bit(l & BIT_7); break;                               // BIT 7,L
            case 0x7E: bit(readMem(getHL()) & BIT_7); break;                // BIT 7,(HL)
            case 0x7F: bit(a & BIT_7); break;                               // BIT 7,A
            case 0x80: b &= ~BIT_0; break;                                  // RES 0,B
            case 0x81: c &= ~BIT_0; break;                                  // RES 0,C
            case 0x82: d &= ~BIT_0; break;                                  // RES 0,D
            case 0x83: e &= ~BIT_0; break;                                  // RES 0,E
            case 0x84: h &= ~BIT_0; break;                                  // RES 0,H
            case 0x85: l &= ~BIT_0; break;                                  // RES 0,L
            case 0x86: writeMem(getHL(), readMem(getHL()) & ~BIT_0); break; // RES 0,(HL)
            case 0x87: a &= ~BIT_0; break;                                  // RES 0,A
            case 0x88: b &= ~BIT_1; break;                                  // RES 1,B
            case 0x89: c &= ~BIT_1; break;                                  // RES 1,C
            case 0x8A: d &= ~BIT_1; break;                                  // RES 1,D
            case 0x8B: e &= ~BIT_1; break;                                  // RES 1,E
            case 0x8C: h &= ~BIT_1; break;                                  // RES 1,H
            case 0x8D: l &= ~BIT_1; break;                                  // RES 1,L
            case 0x8E: writeMem(getHL(), readMem(getHL()) & ~BIT_1); break; // RES 1,(HL)
            case 0x8F: a &= ~BIT_1; break;                                  // RES 1,A
            case 0x90: b &= ~BIT_2; break;                                  // RES 2,B
            case 0x91: c &= ~BIT_2; break;                                  // RES 2,C
            case 0x92: d &= ~BIT_2; break;                                  // RES 2,D
            case 0x93: e &= ~BIT_2; break;                                  // RES 2,E
            case 0x94: h &= ~BIT_2; break;                                  // RES 2,H
            case 0x95: l &= ~BIT_2; break;                                  // RES 2,L
            case 0x96: writeMem(getHL(), readMem(getHL()) & ~BIT_2); break; // RES 2,(HL)
            case 0x97: a &= ~BIT_2; break;                                  // RES 2,A
            case 0x98: b &= ~BIT_3; break;                                  // RES 3,B
            case 0x99: c &= ~BIT_3; break;                                  // RES 3,C
            case 0x9A: d &= ~BIT_3; break;                                  // RES 3,D
            case 0x9B: e &= ~BIT_3; break;                                  // RES 3,E
            case 0x9C: h &= ~BIT_3; break;                                  // RES 3,H
            case 0x9D: l &= ~BIT_3; break;                                  // RES 3,L
            case 0x9E: writeMem(getHL(), readMem(getHL()) & ~BIT_3); break; // RES 3,(HL)
            case 0x9F: a &= ~BIT_3; break;                                  // RES 3,A
            case 0xA0: b &= ~BIT_4; break;                                  // RES 4,B
            case 0xA1: c &= ~BIT_4; break;                                  // RES 4,C
            case 0xA2: d &= ~BIT_4; break;                                  // RES 4,D
            case 0xA3: e &= ~BIT_4; break;                                  // RES 4,E
            case 0xA4: h &= ~BIT_4; break;                                  // RES 4,H
            case 0xA5: l &= ~BIT_4; break;                                  // RES 4,L
            case 0xA6: writeMem(getHL(), readMem(getHL()) & ~BIT_4); break; // RES 4,(HL)
            case 0xA7: a &= ~BIT_4; break;                                  // RES 4,A
            case 0xA8: b &= ~BIT_5; break;                                  // RES 5,B
            case 0xA9: c &= ~BIT_5; break;                                  // RES 5,C
            case 0xAA: d &= ~BIT_5; break;                                  // RES 5,D
            case 0xAB: e &= ~BIT_5; break;                                  // RES 5,E
            case 0xAC: h &= ~BIT_5; break;                                  // RES 5,H
            case 0xAD: l &= ~BIT_5; break;                                  // RES 5,L
            case 0xAE: writeMem(getHL(), readMem(getHL()) & ~BIT_5); break; // RES 5,(HL)
            case 0xAF: a &= ~BIT_5; break;                                  // RES 5,A
            case 0xB0: b &= ~BIT_6; break;                                  // RES 6,B
            case 0xB1: c &= ~BIT_6; break;                                  // RES 6,C
            case 0xB2: d &= ~BIT_6; break;                                  // RES 6,D
            case 0xB3: e &= ~BIT_6; break;                                  // RES 6,E
            case 0xB4: h &= ~BIT_6; break;                                  // RES 6,H
            case 0xB5: l &= ~BIT_6; break;                                  // RES 6,L
            case 0xB6: writeMem(getHL(), readMem(getHL()) & ~BIT_6); break; // RES 6,(HL)
            case 0xB7: a &= ~BIT_6; break;                                  // RES 6,A
            case 0xB8: b &= ~BIT_7; break;                                  // RES 7,B
            case 0xB9: c &= ~BIT_7; break;                                  // RES 7,C
            case 0xBA: d &= ~BIT_7; break;                                  // RES 7,D
            case 0xBB: e &= ~BIT_7; break;                                  // RES 7,E
            case 0xBC: h &= ~BIT_7; break;                                  // RES 7,H
            case 0xBD: l &= ~BIT_7; break;                                  // RES 7,L
            case 0xBE: writeMem(getHL(), readMem(getHL()) & ~BIT_7); break; // RES 7,(HL)
            case 0xBF: a &= ~BIT_7; break;                                  // RES 7,A
            case 0xC0: b |= BIT_0; break;                                   // SET 0,B
            case 0xC1: c |= BIT_0; break;                                   // SET 0,C
            case 0xC2: d |= BIT_0; break;                                   // SET 0,D
            case 0xC3: e |= BIT_0; break;                                   // SET 0,E
            case 0xC4: h |= BIT_0; break;                                   // SET 0,H
            case 0xC5: l |= BIT_0; break;                                   // SET 0,L
            case 0xC6: writeMem(getHL(), readMem(getHL()) | BIT_0); break;  // SET 0,(HL)
            case 0xC7: a |= BIT_0; break;                                   // SET 0,A
            case 0xC8: b |= BIT_1; break;                                   // SET 1,B
            case 0xC9: c |= BIT_1; break;                                   // SET 1,C
            case 0xCA: d |= BIT_1; break;                                   // SET 1,D
            case 0xCB: e |= BIT_1; break;                                   // SET 1,E
            case 0xCC: h |= BIT_1; break;                                   // SET 1,H
            case 0xCD: l |= BIT_1; break;                                   // SET 1,L
            case 0xCE: writeMem(getHL(), readMem(getHL()) | BIT_1); break;  // SET 1,(HL)
            case 0xCF: a |= BIT_1; break;                                   // SET 1,A
            case 0xD0: b |= BIT_2; break;                                   // SET 2,B
            case 0xD1: c |= BIT_2; break;                                   // SET 2,C
            case 0xD2: d |= BIT_2; break;                                   // SET 2,D
            case 0xD3: e |= BIT_2; break;                                   // SET 2,E
            case 0xD4: h |= BIT_2; break;                                   // SET 2,H
            case 0xD5: l |= BIT_2; break;                                   // SET 2,L
            case 0xD6: writeMem(getHL(), readMem(getHL()) | BIT_2); break;  // SET 2,(HL)
            case 0xD7: a |= BIT_2; break;                                   // SET 2,A
            case 0xD8: b |= BIT_3; break;                                   // SET 3,B
            case 0xD9: c |= BIT_3; break;                                   // SET 3,C
            case 0xDA: d |= BIT_3; break;                                   // SET 3,D
            case 0xDB: e |= BIT_3; break;                                   // SET 3,E
            case 0xDC: h |= BIT_3; break;                                   // SET 3,H
            case 0xDD: l |= BIT_3; break;                                   // SET 3,L
            case 0xDE: writeMem(getHL(), readMem(getHL()) | BIT_3); break;  // SET 3,(HL)
            case 0xDF: a |= BIT_3; break;                                   // SET 3,A
            case 0xE0: b |= BIT_4; break;                                   // SET 4,B
            case 0xE1: c |= BIT_4; break;                                   // SET 4,C
            case 0xE2: d |= BIT_4; break;                                   // SET 4,D
            case 0xE3: e |= BIT_4; break;                                   // SET 4,E
            case 0xE4: h |= BIT_4; break;                                   // SET 4,H
            case 0xE5: l |= BIT_4; break;                                   // SET 4,L
            case 0xE6: writeMem(getHL(), readMem(getHL()) | BIT_4); break;  // SET 4,(HL)
            case 0xE7: a |= BIT_4; break;                                   // SET 4,A
            case 0xE8: b |= BIT_5; break;                                   // SET 5,B
            case 0xE9: c |= BIT_5; break;                                   // SET 5,C
            case 0xEA: d |= BIT_5; break;                                   // SET 5,D
            case 0xEB: e |= BIT_5; break;                                   // SET 5,E
            case 0xEC: h |= BIT_5; break;                                   // SET 5,H
            case 0xED: l |= BIT_5; break;                                   // SET 5,L
            case 0xEE: writeMem(getHL(), readMem(getHL()) | BIT_5); break;  // SET 5,(HL)
            case 0xEF: a |= BIT_5; break;                                   // SET 5,A
            case 0xF0: b |= BIT_6; break;                                   // SET 6,B
            case 0xF1: c |= BIT_6; break;                                   // SET 6,C
            case 0xF2: d |= BIT_6; break;                                   // SET 6,D
            case 0xF3: e |= BIT_6; break;                                   // SET 6,E
            case 0xF4: h |= BIT_6; break;                                   // SET 6,H
            case 0xF5: l |= BIT_6; break;                                   // SET 6,L
            case 0xF6: writeMem(getHL(), readMem(getHL()) | BIT_6); break;  // SET 6,(HL)
            case 0xF7: a |= BIT_6; break;                                   // SET 6,A
            case 0xF8: b |= BIT_7; break;                                   // SET 7,B
            case 0xF9: c |= BIT_7; break;                                   // SET 7,C
            case 0xFA: d |= BIT_7; break;                                   // SET 7,D
            case 0xFB: e |= BIT_7; break;                                   // SET 7,E
            case 0xFC: h |= BIT_7; break;                                   // SET 7,H
            case 0xFD: l |= BIT_7; break;                                   // SET 7,L
            case 0xFE: writeMem(getHL(), readMem(getHL()) | BIT_7); break;  // SET 7,(HL)
            case 0xFF: a |= BIT_7; break;                                   // SET 7,A

            // Unimplented CB Opcode
            //default:
            //  System.out.println("Unimplemented CB Opcode: "+Integer.toHexString(opcode));
            //  break;
        }
    }


        

    /**
     *  CB RLC - Rotate Left Carry
     *
     *  @param value        Value to adjust
     *
     *  @return             Adjusted value
     */

    private final int rlc(int value)
    {
        int carry = (value & 0x80) >> 7;
        value = ((value << 1) | (value >> 7)) & 0xff;
        f = carry | SZP_TABLE[value];
        return value;
    }


    /**
     *  CB RRC - Rotate Right Carry
     *
     *  @param value        Value to adjust
     *
     *  @return             Adjusted value
     */

    private final int rrc(int value)
    {
        int carry = (value & 0x01);
        value = ((value >> 1) | (value << 7)) & 0xff;
        f = carry | SZP_TABLE[value];
        return value;
    }


    /**
     *  CB RL - Rotate Left
     *
     *  @param value        Value to adjust
     *
     *  @return             Adjusted value
     */

    private final int rl(int value)
    {
        int carry = (value & 0x80) >> 7;
        value = ((value << 1) | (f & F_CARRY)) & 0xff;
        f = carry | SZP_TABLE[value];
        return value;
    }


    /**
     *  CB RR - Rotate Right
     *
     *  @param value        Value to adjust
     *
     *  @return             Adjusted value
     */

    private final int rr(int value)
    {
        int carry = (value & 0x01);
        value = ((value >> 1) | (f << 7)) & 0xff;
        f = carry | SZP_TABLE[value];
        return value;
    }


    /**
     *  CB SLA - Shift Left Arithmetic
     *
     *  @param value        Value to adjust
     *
     *  @return             Adjusted value
     */

    private final int sla(int value)
    {
         int carry = (value & 0x80) >> 7;
         value = (value << 1) & 0xff;
         f = carry | SZP_TABLE[value];
         return value;
    }

    
    /**
     *  CB SLL - Logical Left Shift
     *
     *  @param value        Value to adjust
     *
     *  @return             Adjusted value
     */

    private final int sll(int value)
    {
        int carry = (value & 0x80) >> 7;
        value = ((value << 1) | 1) & 0xff;
        f = carry | SZP_TABLE[value];
        return value;
    }

    /**
     *  CB SRA - Shift Right Arithmetic
     *
     *  @param value        Value to adjust
     *
     *  @return             Adjusted value
     */

    private final int sra(int value)
    {
         int carry = value & 0x01;
         value = (value >> 1) | (value & 0x80);
         f = carry | SZP_TABLE[value];
        return value;
    }

    /**
     *  CB SRL - Logical Shift Right
     *
     *  @param value        Value to adjust
     *
     *  @return             Adjusted value
     */

    private final int srl(int value)
    {
         int carry = value & 0x01;
         value = (value >> 1) & 0xff;
         f = carry | SZP_TABLE[value];
        return value;
    }

    /**
     *  CB BIT - Test Bit
     *
     *  @param value        Masked value
     */

    private final void bit(int mask)
    {
        f = (f & F_CARRY) | SZ_BIT_TABLE[mask];
    }


    /**
     *  Execute DD/FD Prefixed Index Opcode
     *
     *  @param opcode       Opcode hex value
     *  @param index        Index Register to use
     */

    private final void doIndexOpIX(int opcode)
    {
        tstates -= OP_DD_STATES[opcode];
        
        if (Setup.REFRESH_EMULATION)
            incR();

        switch(opcode)
        {
            case 0x09: setIX(add16(getIX(), getBC())); break;             // ADD IX,BC
            case 0x19: setIX(add16(getIX(), getDE())); break;             // ADD IX,DE
            case 0x21: setIX(readMemWord(pc)); pc+=2; break;              // LD IX,nn
            case 0x22: {                                                  // LD (nn),IX
                int location = readMemWord(pc);
                writeMem(location++, ixL);
                writeMem(location, ixH);
                pc+=2;
             } break;
            case 0x23: incIX(); break;                                    // INC IX
            case 0x24: ixH = inc8(ixH); break;                            // INC IXh *
            case 0x25: ixH = dec8(ixH); break;                            // DEC IXh *
            case 0x26: ixH = readMem(pc++); break;                        // LD IXh,n *
            case 0x29: setIX(add16(getIX(), getIX())); break;             // ADD IX,IX
            case 0x2A: {                                                  // LD IX,(nn)
                int location = readMemWord(pc);
                ixL = readMem(location);
                ixH = readMem(++location);
                pc+=2;         
            } break;
            case 0x2B: decIX(); break;                                    // DEC IX
            case 0x2C: ixL = inc8(ixL); break;                            // INC IXl *
            case 0x2D: ixL = dec8(ixL); break;                            // DEC IXl *
            case 0x2E: ixL = readMem(pc++); break;                        // LD IXl,n
            case 0x34: incMem(getIX()+d()); pc++; break;                  // INC (IX+d)
            case 0x35: decMem(getIX()+d()); pc++; break;                  // DEC (IX+d)
            case 0x36: writeMem(getIX()+d(), readMem(++pc)); pc++; break; // LD (IX+d), n
            case 0x39: setIX(add16(getIX(), sp)); break;                  // ADD IX,SP
            case 0x44: b = ixH; break;                                    // LD B,IXh *
            case 0x45: b = ixL; break;                                    // LD B,IXl *
            case 0x46: b = readMem(getIX()+d()); pc++; break;             // LD B,(IX+d)
            case 0x4C: c = ixH; break;                                    // LD C,IXh *
            case 0x4D: c = ixL; break;                                    // LD C,IXl *
            case 0x4E: c = readMem(getIX()+d()); pc++; break;             // LD C,(IX+d)
            case 0x54: d = ixH; break;                                    // LD D,IXh *
            case 0x55: d = ixL; break;                                    // LD D,IXl *
            case 0x56: d = readMem(getIX()+d()); pc++; break;             // LD D,(IX+d)
            case 0x5C: e = ixH; break;                                    // LD E,IXh *
            case 0x5D: e = ixL; break;                                    // LD E,IXl *
            case 0x5E: e = readMem(getIX()+d()); pc++; break;             // LD E,(IX+d)
            case 0x60: ixH = b; break;                                    // LD IXh,B *
            case 0x61: ixH = c; break;                                    // LD IXh,C *
            case 0x62: ixH = d; break;                                    // LD IXh,D *
            case 0x63: ixH = e; break;                                    // LD IXh,E *
            case 0x64: break;                                             // LD IXH,IXH*
            case 0x65: ixH = ixL; break;                                  // LD IXH,IXL *
            case 0x66: h = readMem(getIX()+d()); pc++; break;             // LD H,(IX+d)
            case 0x67: ixH = a; break;                                    // LD IXh,A *
            case 0x68: ixL = b; break;                                    // LD IXL,B *
            case 0x69: ixL = c; break;                                    // LD IXl,C *
            case 0x6A: ixL = d; break;                                    // LD IXL,D *
            case 0x6B: ixL = e; break;                                    // LD IXl,E *
            case 0x6C: ixL = ixH; break;                                  // LD IXl,IXh *
            case 0x6D: break;                                             // LD IXl,IXl *
            case 0x6E: l = readMem(getIX()+d());  pc++; break;            // LD L,(IX+d)
            case 0x6F: ixL = a; break;                                    // LD IXl,A *
            case 0x70: writeMem(getIX()+d(), b); pc++; break;             // LD (IX+d),B
            case 0x71: writeMem(getIX()+d(), c); pc++; break;             // LD (IX+d),C
            case 0x72: writeMem(getIX()+d(), d); pc++; break;             // LD (IX+d),D
            case 0x73: writeMem(getIX()+d(), e); pc++; break;             // LD (IX+d),E
            case 0x74: writeMem(getIX()+d(), h); pc++; break;             // LD (IX+d),H
            case 0x75: writeMem(getIX()+d(), l); pc++; break;             // LD (IX+d),L
            case 0x77: writeMem(getIX()+d(), a); pc++; break;             // LD (IX+d),A
            case 0x7C: a = ixH; break;                                    // LD A,IXh *
            case 0x7D: a = ixL; break;                                    // LD A,IXl *
            case 0x7E: a = readMem(getIX()+d()); pc++; break;             // LD A,(IX+d)
            case 0x84: add_a(ixH); break;                                 // ADD A,IXh *
            case 0x85: add_a(ixL); break;                                 // ADD A,IXl *
            case 0x86: add_a(readMem(getIX()+d())); pc++; break;          // ADD A,(IX+d)
            case 0x8C: adc_a(ixH); break;                                 // ADC A,IXH *
            case 0x8D: adc_a(ixL); break;                                 // ADC A,IXL *
            case 0x8E: adc_a(readMem(getIX()+d())); pc++; break;          // ADC A,(IX+d)
            case 0x94: sub_a(ixH); break;                                 // SUB IXh *
            case 0x95: sub_a(ixL); break;                                 // SUB IXl *
            case 0x96: sub_a(readMem(getIX()+d())); pc++; break;          // SUB A,(IX+d)
            case 0x9C: sbc_a(ixH); break;                                 // SBC A,IXH *
            case 0x9D: sbc_a(ixL); break;                                 // SBC A,IXL *
            case 0x9E: sbc_a(readMem(getIX()+d())); pc++; break;          // SBC A,(IX+d)
            case 0xA4: f = SZP_TABLE[a &= ixH] | F_HALFCARRY; break;      // AND IXh *
            case 0xA5: f = SZP_TABLE[a &= ixL] | F_HALFCARRY; break;      // AND IXl *
            case 0xA6: f = SZP_TABLE[a &= readMem(getIX()+d())] | F_HALFCARRY;  
                       pc++; break;                                       // AND A,(IX+d)
            case 0xAC: f = SZP_TABLE[a ^= ixH]; break;                    // XOR A IXH*
            case 0xAD: f = SZP_TABLE[a ^= ixL]; break;                    // XOR A IXL*
            case 0xAE: f = SZP_TABLE[a ^= readMem(getIX()+d())];pc++;break;// XOR A,(IX+d)
            case 0xB4: f = SZP_TABLE[a |= ixH]; break;                    // OR A IXH*
            case 0xB5: f = SZP_TABLE[a |= ixL]; break;                    // OR A IXL*
            case 0xB6: f = SZP_TABLE[a |= readMem(getIX()+d())];pc++;break;// OR A,(IX+d)
            case 0xBC: cp_a(ixH); break;                                  // CP IXh *
            case 0xBD: cp_a(ixL); break;                                  // CP IXl *
            case 0xBE: cp_a(readMem(getIX()+d())); pc++; break;           // CP (IX+d)
            case 0xCB: doIndexCB(getIX()); break;                         // CB Opcode
            case 0xE1: setIX(readMemWord(sp)); sp+=2; break;              // POP IX
            case 0xE3: {int temp = getIX(); setIX(readMemWord(sp));       // EX SP,(IX)
                       writeMem(sp, temp&0xff); writeMem(sp+1, temp>>8);
                       }break;
            case 0xE5: push(ixH, ixL); break;                             // PUSH IX
            case 0xE9: pc = getIX(); break;                               // JP (IX)
            case 0xF9: sp = getIX(); break;                               // LD SP,IX

            // Unimplented DD/FD Opcode
            default:
                pc--;
                break;
        } // end of switch
    }
    
    private final void doIndexOpIY(int opcode)
    {
        tstates -= OP_DD_STATES[opcode];
        
        if (Setup.REFRESH_EMULATION)
            incR();

        switch(opcode)
        {
            case 0x09: setIY(add16(getIY(), getBC())); break;             // ADD IY,BC
            case 0x19: setIY(add16(getIY(), getDE())); break;             // ADD IY,DE
            case 0x21: setIY(readMemWord(pc)); pc+=2; break;              // LD IY,nn
            case 0x22: {                                                  // LD (nn),IY
                int location = readMemWord(pc);
                writeMem(location++, iyL);
                writeMem(location, iyH);
                pc+=2;
             } break;
            case 0x23: incIY(); break;                                    // INC IY
            case 0x24: iyH = inc8(iyH); break;                            // INC IYh *
            case 0x25: iyH = dec8(iyH); break;                            // DEC IYh *
            case 0x26: iyH = readMem(pc++); break;                        // LD IYh,n *
            case 0x29: setIY(add16(getIY(), getIY())); break;             // ADD IY,IY
            case 0x2A: {                                                  // LD IY,(nn)
                int location = readMemWord(pc);
                iyL = readMem(location);
                iyH = readMem(++location);
                pc+=2;         
            } break;
            case 0x2B: decIY(); break;                                    // DEC IY
            case 0x2C: iyL = inc8(iyL); break;                            // INC IYl *
            case 0x2D: iyL = dec8(iyL); break;                            // DEC IYl *
            case 0x2E: iyL = readMem(pc++); break;                        // LD IYl,n
            case 0x34: incMem(getIY()+d()); pc++; break;                  // INC (IY+d)
            case 0x35: decMem(getIY()+d()); pc++; break;                  // DEC (IY+d)
            case 0x36: writeMem(getIY()+d(), readMem(++pc)); pc++; break; // LD (IY+d), n
            case 0x39: setIY(add16(getIY(), sp)); break;                  // ADD IY,SP
            case 0x44: b = iyH; break;                                    // LD B,IYh *
            case 0x45: b = iyL; break;                                    // LD B,IYl *
            case 0x46: b = readMem(getIY()+d()); pc++; break;             // LD B,(IY+d)
            case 0x4C: c = iyH; break;                                    // LD C,IYh *
            case 0x4D: c = iyL; break;                                    // LD C,IYl *
            case 0x4E: c = readMem(getIY()+d()); pc++; break;             // LD C,(IY+d)
            case 0x54: d = iyH; break;                                    // LD D,IYh *
            case 0x55: d = iyL; break;                                    // LD D,IYl *
            case 0x56: d = readMem(getIY()+d()); pc++; break;             // LD D,(IY+d)
            case 0x5C: e = iyH; break;                                    // LD E,IYh *
            case 0x5D: e = iyL; break;                                    // LD E,IYl *
            case 0x5E: e = readMem(getIY()+d()); pc++; break;             // LD E,(IY+d)
            case 0x60: iyH = b; break;                                    // LD IYh,B *
            case 0x61: iyH = c; break;                                    // LD IYh,C *
            case 0x62: iyH = d; break;                                    // LD IYh,D *
            case 0x63: iyH = e; break;                                    // LD IYh,E *
            case 0x64: break;                                             // LD IYH,IYH*
            case 0x65: iyH = iyL; break;                                  // LD IYH,IYL *
            case 0x66: h = readMem(getIY()+d()); pc++; break;             // LD H,(IY+d)
            case 0x67: iyH = a; break;                                    // LD IYh,A *
            case 0x68: iyL = b; break;                                    // LD IYL,B *
            case 0x69: iyL = c; break;                                    // LD IYl,C *
            case 0x6A: iyL = d; break;                                    // LD IYL,D *
            case 0x6B: iyL = e; break;                                    // LD IYl,E *
            case 0x6C: iyL = iyH; break;                                  // LD IYl,IYh *
            case 0x6D: break;                                             // LD IYl,IYl *
            case 0x6E: l = readMem(getIY()+d());  pc++; break;            // LD L,(IY+d)
            case 0x6F: iyL = a; break;                                    // LD IYl,A *
            case 0x70: writeMem(getIY()+d(), b); pc++; break;             // LD (IY+d),B
            case 0x71: writeMem(getIY()+d(), c); pc++; break;             // LD (IY+d),C
            case 0x72: writeMem(getIY()+d(), d); pc++; break;             // LD (IY+d),D
            case 0x73: writeMem(getIY()+d(), e); pc++; break;             // LD (IY+d),E
            case 0x74: writeMem(getIY()+d(), h); pc++; break;             // LD (IY+d),H
            case 0x75: writeMem(getIY()+d(), l); pc++; break;             // LD (IY+d),L
            case 0x77: writeMem(getIY()+d(), a); pc++; break;             // LD (IY+d),A
            case 0x7C: a = iyH; break;                                    // LD A,IYh *
            case 0x7D: a = iyL; break;                                    // LD A,IYl *
            case 0x7E: a = readMem(getIY()+d()); pc++; break;             // LD A,(IY+d)
            case 0x84: add_a(iyH); break;                                 // ADD A,IYh *
            case 0x85: add_a(iyL); break;                                 // ADD A,IYl *
            case 0x86: add_a(readMem(getIY()+d())); pc++; break;          // ADD A,(IY+d)
            case 0x8C: adc_a(iyH); break;                                 // ADC A,IYH *
            case 0x8D: adc_a(iyL); break;                                 // ADC A,IYL *
            case 0x8E: adc_a(readMem(getIY()+d())); pc++; break;          // ADC A,(IY+d)
            case 0x94: sub_a(iyH); break;                                 // SUB IYh *
            case 0x95: sub_a(iyL); break;                                 // SUB IYl *
            case 0x96: sub_a(readMem(getIY()+d())); pc++; break;          // SUB A,(IY+d)
            case 0x9C: sbc_a(iyH); break;                                 // SBC A,IYH *
            case 0x9D: sbc_a(iyL); break;                                 // SBC A,IYL *
            case 0x9E: sbc_a(readMem(getIY()+d())); pc++; break;          // SBC A,(IY+d)
            case 0xA4: f = SZP_TABLE[a &= iyH] | F_HALFCARRY; break;      // AND IYh *
            case 0xA5: f = SZP_TABLE[a &= iyL] | F_HALFCARRY; break;      // AND IYl *
            case 0xA6: f = SZP_TABLE[a &= readMem(getIY()+d())] | F_HALFCARRY;  
                       pc++; break;                                       // AND A,(IY+d)
            case 0xAC: f = SZP_TABLE[a ^= iyH]; break;                    // XOR A IYH*
            case 0xAD: f = SZP_TABLE[a ^= iyL]; break;                    // XOR A IYL*
            case 0xAE: f = SZP_TABLE[a ^= readMem(getIY()+d())];pc++;break;// XOR A,(IY+d)
            case 0xB4: f = SZP_TABLE[a |= iyH]; break;                    // OR A IYH*
            case 0xB5: f = SZP_TABLE[a |= iyL]; break;                    // OR A IYL*
            case 0xB6: f = SZP_TABLE[a |= readMem(getIY()+d())];pc++;break;// OR A,(IY+d)
            case 0xBC: cp_a(iyH); break;                                  // CP IYh *
            case 0xBD: cp_a(iyL); break;                                  // CP IYl *
            case 0xBE: cp_a(readMem(getIY()+d())); pc++; break;           // CP (IY+d)
            case 0xCB: doIndexCB(getIY()); break;                         // CB Opcode
            case 0xE1: setIY(readMemWord(sp)); sp+=2; break;              // POP IY
            case 0xE3: {int temp = getIY(); setIY(readMemWord(sp));       // EX SP,(IY)
                       writeMem(sp, temp&0xff); writeMem(sp+1, temp>>8);
                       }break;
            case 0xE5: push(iyH, iyL); break;                             // PUSH IY
            case 0xE9: pc = getIY(); break;                               // JP (IY)
            case 0xF9: sp = getIY(); break;                               // LD SP,IY
            
            // Unimplented DD/FD Opcode
            default:
                pc--;
                break;
        } // end of switch
    }

    /**
     *  Execute DDCB/FDCB Prefixed Opcode
     *
     *  @param index        Index Register To Use
     */

    private final void doIndexCB(int index)
    {
        int location = (index + d())&0xFFFF;
        int opcode = readMem(++pc);
        tstates -= OP_INDEX_CB_STATES[opcode];

        switch (opcode)
        {
            case 0x06: writeMem(location, rlc(readMem(location))); break;       // RLC (IX)
            case 0x0E: writeMem(location, rrc(readMem(location)));  break;      // RRC (IX)
            case 0x16: writeMem(location, rl(readMem(location))); break;        // RL (IX)
            case 0x1E: writeMem(location, rr(readMem(location))); break;        // RR (IX)
            case 0x26: writeMem(location, sla(readMem(location))); break;       // SLA (IX)
            case 0x2E: writeMem(location, sra(readMem(location))); break;       // SRA (IX)
            case 0x36: writeMem(location, sll(readMem(location))); break;       // SLL (IX) *
            case 0x3E: writeMem(location, srl(readMem(location))); break;       // SRL (IX)
            case 0x46: bit(readMem(location) & BIT_0); break;                   // BIT 0,(IX)
            case 0x4E: bit(readMem(location) & BIT_1); break;                   // BIT 1,(IX)
            case 0x56: bit(readMem(location) & BIT_2); break;                   // BIT 2,(IX)
            case 0x5E: bit(readMem(location) & BIT_3); break;                   // BIT 3,(IX)
            case 0x66: bit(readMem(location) & BIT_4); break;                   // BIT 4,(IX)
            case 0x6E: bit(readMem(location) & BIT_5); break;                   // BIT 5,(IX)
            case 0x76: bit(readMem(location) & BIT_6); break;                   // BIT 6,(IX)
            case 0x7E: bit(readMem(location) & BIT_7); break;                   // BIT 7,(IX)
            case 0x86: writeMem(location, readMem(location) & ~BIT_0); break;   // RES 0,(IX)
            case 0x8E: writeMem(location, readMem(location) & ~BIT_1); break;   // RES 1,(IX)
            case 0x96: writeMem(location, readMem(location) & ~BIT_2); break;   // RES 2,(IX)
            case 0x9E: writeMem(location, readMem(location) & ~BIT_3); break;   // RES 3,(IX)
            case 0xA6: writeMem(location, readMem(location) & ~BIT_4); break;   // RES 4,(IX)
            case 0xAE: writeMem(location, readMem(location) & ~BIT_5); break;   // RES 5,(IX)
            case 0xB6: writeMem(location, readMem(location) & ~BIT_6); break;   // RES 6,(IX)
            case 0xBE: writeMem(location, readMem(location) & ~BIT_7); break;   // RES 7,(IX)
            case 0xC6: writeMem(location, readMem(location) | BIT_0); break;    // SET 0,(IX)
            case 0xCE: writeMem(location, readMem(location) | BIT_1); break;    // SET 1,(IX)
            case 0xD6: writeMem(location, readMem(location) | BIT_2); break;    // SET 2,(IX)
            case 0xDE: writeMem(location, readMem(location) | BIT_3); break;    // SET 3,(IX)
            case 0xE6: writeMem(location, readMem(location) | BIT_4); break;    // SET 4,(IX)
            case 0xEE: writeMem(location, readMem(location) | BIT_5); break;    // SET 5,(IX)
            case 0xF6: writeMem(location, readMem(location) | BIT_6); break;    // SET 6,(IX)
            case 0xFE: writeMem(location, readMem(location) | BIT_7); break;    // SET 7,(IX)

            //default:
            //  System.out.println("Unimplemented DDCB or FDCB Opcode: "+Integer.toHexString(opcode&0xff));
            //  break;

        } // end of switch
        pc++;
    }


    /**
     *  Execute ED Prefixed Opcode
     *
     *  @param opcode       Opcode hex value
     */

    private final void doED(int opcode)
    {
        int temp;

        tstates -= OP_ED_STATES[opcode];
        
        if (Setup.REFRESH_EMULATION)
            incR();

        switch(opcode)
        {
            //  -- ED40 IN B,(C) -------------------------
            case 0x40: b = port.in(c); f = (f & F_CARRY) | SZP_TABLE[b]; pc++; break;

            //  -- ED41 OUT (C),B -------------------------
            case 0x41: port.out(c, b); pc++; break;

            // --  ED42 SBC HL, BC ------------------------
            case 0x42: sbc16(getBC()); pc++; break;

            //  -- ED43 LD (nn),BC ------------------------
            case 0x43:
            {
                int location = readMemWord(pc+1);
                writeMem(location++, c);
                writeMem(location, b);
                pc+=3;
            }
                break;

            //  -- ED44 NEG -------------------------------
            case 0x44:
            case 0x4C:
            case 0x54:
            case 0x5C:
            case 0x64:
            case 0x6C:
            case 0x74:
            case 0x7C:
            {
                // A <- 0-A
                int a_copy = a;
                a = 0;
                sub_a(a_copy);
                pc++;
            }
                break;

            //  -- ED45 RETN / RETI ------------------------------
            case 0x45:
            case 0x4D:
            case 0x55:
            case 0x5D:
            case 0x65:
            case 0x6D:
            case 0x75:
            case 0x7D:
                pc = readMemWord(sp);
                sp+=2;
                iff1 = iff2;
                break;

            //  -- ED46 IM 0-------------------------------
            case 0x46:
            case 0x4E:
            case 0x66:
            case 0x6E:
                im = 0;
                pc++;
                break;

            //  -- ED47 LD I, A ---------------------------
            case 0x47: i = a; pc++; break;

            //  -- ED48 IN C,(C) -------------------------
            case 0x48:
                c = port.in(c);
                f = (f & F_CARRY) | SZP_TABLE[c];
                pc++;
                break;

            //  -- ED49 OUT (C),C -------------------------
            case 0x49: port.out(c, c); pc++; break;

            //  -- ED4A ADC HL, BC ------------------------
            case 0x4A: adc16(getBC()); pc++; break;

            //  -- ED4B LD BC, (nn) -----------------------
            case 0x4B:
            {
                int location = readMemWord(pc+1);
                c = readMem(location++);
                b = readMem(location);
                pc+=3;
            }
                break;

            //  -- ED4F LD R, A ---------------------------
            case 0x4F: r = a; pc++; break;

            //  -- ED50 IN D,(C) -------------------------
            case 0x50:
                d = port.in(c);
                f = (f & F_CARRY) | SZP_TABLE[d];
                pc++;
                break;

            //  -- ED51 OUT (C),D -------------------------
            case 0x51: port.out(c, d); pc++; break;

            // --  ED52 SBC HL, DE ------------------------
            case 0x52: sbc16(getDE()); pc++; break;

            //  -- ED53 LD (nn),DE ------------------------
            case 0x53:
            {
                int location = readMemWord(pc+1);
                writeMem(location++, e);        //SPl
                writeMem(location, d);  //SPh
                pc+=3;
            }
                break;

            //  -- ED56 IM 1-------------------------------
            case 0x56:
            case 0x76: im = 1; pc++; break;

            //  -- ED57 LD A, I ---------------------------
            case 0x57:
                a = i;
                f = (f & F_CARRY) | SZ_TABLE[a] | (iff2 ? F_PARITY : 0);
                pc++;
                break;

            //  -- ED58 IN E,(C) -------------------------
            case 0x58:
                e = port.in(c);
                f = (f & F_CARRY) | SZP_TABLE[e];
                pc++;
                break;

            //  -- ED59 OUT (C),E -------------------------
            case 0x59: port.out(c, e); pc++; break;

            //  -- ED5A ADC HL, DE ------------------------
            case 0x5A: adc16(getDE()); pc++; break;

            //  -- ED5B LD DE, (nn) -----------------------
            case 0x5B:
            {
                int location = readMemWord(pc+1);
                e = readMem(location++);
                d = readMem(location);
                pc+=3;
            }
                break;

            // -- ED5F LD A,R -----------------------------
            case 0x5F:
                // Note, to fake refresh emulation we use the random number generator 
                a = Setup.REFRESH_EMULATION ? r : Engine.rndInt(255);
                f = (f & F_CARRY) | SZ_TABLE[a] | (iff2 ? F_PARITY : 0);
                pc++;
                break;

            //  -- ED60 IN H,(C) -------------------------
            case 0x60:
                h = port.in(c);
                f = (f & F_CARRY) | SZP_TABLE[h];
                pc++;
                break;

            //  -- ED61 OUT (C),H -------------------------
            case 0x61: port.out(c, h); pc++; break;

            // --  ED62 SBC HL, HL ------------------------
            case 0x62: sbc16(getHL()); pc++; break;

            //  -- ED63 LD (nn),HL ------------------------
            case 0x63:
            {
                int location = readMemWord(pc+1);
                writeMem(location++, l);        //SPl
                writeMem(location, h);  //SPh
                pc+=3;
            }
                break;

            //  -- ED67 RRD -------------------------------
            case 0x67:
            {
                int location = getHL();
                int hlmem = readMem(location);
   
                // move high 4 of hl to low 4 of hl             
                // move low 4 of a to high 4 of hl
                writeMem(location, (hlmem >> 4) | ((a & 0x0f) << 4));
                // move 4 lowest bits of hl to low 4 of a
                a = (a & 0xF0) | (hlmem & 0x0F);
                
                f = (f & F_CARRY) | SZP_TABLE[a];
                pc++;
            }
                break;

            //  -- ED68 IN L,(C) --------------------------
            case 0x68:
                l = port.in(c);
                f = (f & F_CARRY) | SZP_TABLE[l];
                pc++;
                break;

            //  -- ED69 OUT (C),L -------------------------
            case 0x69: port.out(c, l); pc++; break;

            //  -- ED6A ADC HL, HL ------------------------
            case 0x6A: adc16(getHL()); pc++; break;

            //  -- ED6B LD HL,(nn) -----------------------
            case 0x6B:
            {
                int location = readMemWord(pc+1);
                l = readMem(location++);
                h = readMem(location);
                pc+=3;
            }
                break;

            //  -- ED6F RLD -------------------------------
            case 0x6F:
            {
                int location = getHL();
                int hlmem = readMem(location);
                
                // move low 4 of hl to high 4 of hl
                // move low 4 of a to low 4 of hl
                writeMem(location, (hlmem & 0x0F)<<4 | (a & 0x0F));
                
                // move high 4 of hl to low 4 of a
                a = (a & 0xF0) | (hlmem >> 4);
                
                f = (f & F_CARRY) | SZP_TABLE[a];           
                pc++;
            }
                break;

            //  *- ED71 OUT (C),0 -------------------------
            case 0x71: port.out(c, 0); pc++; break;

            // --  ED72 SBC HL, SP ------------------------
            case 0x72: sbc16(sp); pc++; break;

            //  -- ED73 LD (nn),SP ------------------------
            case 0x73:
            {
                int location = readMemWord(pc+1);
                writeMem(location++, sp&0xff);      //SPl
                writeMem(location, sp>>8);  //SPh
                pc+=3;
            }
                break;

            //  -- ED78 IN A,(C) -------------------------
            case 0x78:
                a = port.in(c);     
                f = (f & F_CARRY) | SZP_TABLE[a];
                pc++;
                break;

            //  -- ED79 OUT (C),A -------------------------
            case 0x79: port.out(c, a); pc++; break;

            // --  ED7A ADC HL, SP ------------------------
            case 0x7A: adc16(sp); pc++; break;

            //  -- ED7B LD SP, (nn) -----------------------
            case 0x7B:  
                sp = readMemWord(readMemWord(pc+1));            
                pc+=3;
                break;

            //  -- EDA0 LDI ----------------------------------
            case 0xA0:
                // (DE) <- (HL)
                writeMem(getDE(), readMem(getHL()));
                incDE(); incHL(); decBC();
                f = (f & 0xC1) | (getBC() != 0 ? F_PARITY : 0);
                pc++;
                break;

            //  -- EDA1 CPI ------------------------------
            case 0xA1:
                temp = (f & F_CARRY) | F_NEGATIVE;
                cp_a(readMem(getHL())); // sets some flags
                incHL();
                decBC();

                temp |= (getBC() == 0 ? 0 : F_PARITY);

                f = (f & 0xF8) | temp;
                pc++;
                break;

            //  -- EDA2 INI -------------------------------
            case 0xA2:
                temp = port.in(c);
                writeMem(getHL(), temp);
                b = dec8(b);
                incHL();
                if ((temp & 0x80) == 0x80) f |= F_NEGATIVE;
                else f &=~ F_NEGATIVE;
                pc++;
                // undocumented flags not finished.
                break;

            //  -- EDA3 OUTI ------------------------------
            // see p14 of undocumented z80 for additional flag info
            case 0xA3:
                temp = readMem(getHL());
                // (C) <- (HL)
                port.out(c, temp);
                // HL <- HL + 1
                incHL();
                // B <- B -1
                b = dec8(b); // Flags in OUTI adjusted in same way as dec b anyway.
                if ((l + temp) > 255)
                {
                    f |= F_CARRY; f |= F_HALFCARRY;
                }
                else
                {
                    f &=~ F_CARRY; f &=~ F_HALFCARRY;
                }
                if ((temp & 0x80) == 0x80) f |= F_NEGATIVE;
                else f &=~ F_NEGATIVE;
                pc++;
                break;

            //  -- EDA8 LDD ----------------------------------
            case 0xA8:
                // (DE) <- (HL)
                writeMem(getDE(), readMem(getHL()));
                decDE(); decHL(); decBC();
                f = (f & 0xC1) | (getBC() != 0 ? F_PARITY : 0);
                pc++;
                break;

            //  -- EDA9 CPD ------------------------------ 
            case 0xA9:
                temp = (f & F_CARRY) | F_NEGATIVE;
                cp_a(readMem(getHL())); // sets some flags
                decHL();
                decBC();

                temp |= (getBC() == 0 ? 0 : F_PARITY);

                f = (f & 0xF8) | temp;
                pc++;
                break;

            //  -- EDAA IND -------------------------------
            case 0xAA:
                temp = port.in(c);
                writeMem(getHL(), temp);
                b = dec8(b);
                decHL();
                if ((temp & 0x80) != 0) f |= F_NEGATIVE;
                else f &=~ F_NEGATIVE;
                pc++;
                // undocumented flags not finished.
                break;

            //  -- EDAB OUTD ------------------------------
            // see p14 of undocumented z80 for additional flag info
            case 0xAB:
                temp = readMem(getHL());
                // (C) <- (HL)
                port.out(c, temp);
                // HL <- HL - 1
                decHL();
                // B <- B -1
                b = dec8(b); // Flags in OUTI adjusted in same way as dec b anyway.

                if ((l + temp) > 255)
                {
                    f |= F_CARRY; f |= F_HALFCARRY;
                }
                else
                {
                    f &=~ F_CARRY; f &=~ F_HALFCARRY;
                }
                if ((temp & 0x80) == 0x80) f |= F_NEGATIVE;
                else f &=~ F_NEGATIVE;
                pc++;
                break;

            //  -- EDB0 LDIR ------------------------------
            case 0xB0:
                writeMem(getDE(), readMem(getHL()));
                incDE();
                incHL();
                decBC();

                if (getBC() != 0)
                {
                    f |= F_PARITY;
                    tstates-=5;
                    pc--;
                }
                else
                {
                    f &=~ F_PARITY;
                    pc++;
                }

                f &=~ F_NEGATIVE; f &=~ F_HALFCARRY;
                break;

            //  -- EDB1 CPIR ------------------------------
            case 0xB1:  
                temp = (f & F_CARRY) | F_NEGATIVE;
                cp_a(readMem(getHL())); // sets zero flag for us
                incHL();
                decBC();

                temp |= getBC() == 0 ? 0 : F_PARITY;

                // Repeat instruction until a = (hl) or bc == 0
                if (((temp & F_PARITY) != 0) && ((f & F_ZERO) == 0))
                {
                    tstates -=5;
                    pc--;
                }
                else
                {
                    pc++;
                }

                f = (f & 0xF8) | temp; // Sign set by the cp instruction
                break;

            //  -- EDB2 INIR ------------------------------
            case 0xB2:
                temp = port.in(c);
                writeMem(getHL(), temp);
                b = dec8(b);
                incHL();
                if (b != 0)
                {
                    tstates-=5;
                    pc--;
                }
                else
                {
                    pc++;
                }

                if ((temp & 0x80) == 0x80) f |= F_NEGATIVE;
                else f &=~ F_NEGATIVE;
                // undocumented flags not finished.
                break;

            //  -- EDB3 OTIR ------------------------------
            case 0xB3:
                temp = readMem(getHL());
                // (C) <- (HL)
                port.out(c, temp);
                // B <- B -1
                b = dec8(b);
                // HL <- HL + 1
                incHL();

                if (b != 0)
                {
                    tstates-=5;
                    pc--;
                }
                else
                {
                    pc++;
                }
                if ((l + temp) > 255)
                {
                    f |= F_CARRY; f |= F_HALFCARRY;
                }
                else
                {
                    f &=~ F_CARRY; f &=~ F_HALFCARRY;
                }

                if ((temp & 0x80) != 0) f |= F_NEGATIVE;
                else f &=~ F_NEGATIVE;
                break;

            // -- EDB8 LDDR ---------------------------------
            case 0xB8:
                writeMem(getDE(), readMem(getHL()));
                decDE(); decHL(); decBC();

                if (getBC() != 0)
                {
                    f |= F_PARITY;
                    tstates-=5;
                    pc--;
                }
                else
                {
                    f &=~ F_PARITY;
                    pc++;
                }

                f &=~ F_NEGATIVE; f &=~ F_HALFCARRY;
                break;

            // -- EDB9 CPDR ------------------------------------
            case 0xB9:
                temp = (f & F_CARRY) | F_NEGATIVE;              
                cp_a(readMem(getHL())); // sets zero flag for us
                decHL();
                decBC();

                temp |= (getBC() == 0 ? 0 : F_PARITY);

                // Repeat instruction until a = (hl) or bc == 0
                if (((temp & F_PARITY) != 0) && ((f & F_ZERO) == 0))
                {
                    tstates -=5;
                    pc--;
                }
                else
                {
                    pc++;
                }

                f = (f & 0xF8) | temp;
                break;

            //  -- EDBA INDR ------------------------------
            case 0xBA:
                temp = port.in(c);
                writeMem(getHL(), temp);
                b = dec8(b);
                decHL();
                if (b != 0)
                {
                    tstates -= 5;
                    pc--;
                }
                else
                {
                    pc++;
                }

                if ((temp & 0x80) != 0) f |= F_NEGATIVE;
                else f &=~ F_NEGATIVE;
                // undocumented flags not finished.
                break;

            //  -- EDBB OTDR ------------------------------
            case 0xBB:
                temp = readMem(getHL());
                // (C) <- (HL)
                port.out(c, temp);
                // B <- B -1
                b = dec8(b);
                // HL <- HL + 1
                decHL();

                if (b != 0)
                {
                    tstates-=5;
                    pc--;
                }
                else
                {
                    pc++;
                }
                if ((l + temp) > 255)
                {
                    f |= F_CARRY; f |= F_HALFCARRY;
                }
                else
                {
                    f &=~ F_CARRY; f &=~ F_HALFCARRY;
                }

                if ((temp & 0x80) != 0) f |= F_NEGATIVE;
                else f &=~ F_NEGATIVE;
                break;

                // -- Unimplented ED Opcode --------------------
                //default:
                //  System.out.println("Unimplemented ED Opcode: "+Integer.toHexString(opcode));
                //  pc++;
                //  break;
        } // end of switch
    } // end of ed ops


    /**
     * Pre-calculate DAA Table
     * 
     * Address:
     * 
     * Bottom 8 bytes = a value
     * Byte 9  = carry flag
     * Byte 10 = neative flag
     * Byte 11 = halfcarry flag
     * 
     * Returned Value:
     * 
     * a register stored in lower 8 bits
     * f register stored in higher 8 bits
     */
    
    private void generateDAATable()
    {
        DAA_TABLE = new short[0x800];
        
        // Iterate all possible values of a register (0 to 0xFF)
        for (int i = 256; i-- != 0;)
        {
            // Iterate carry / not-carry set
            for (int c = 0; c <= 1; c++)
            {
                // Iterate halfcarry / not-halfcarry set
                for (int h = 0; h <= 1; h++)
                {
                    // Iterate negative / not-negative set
                    for (int n = 0; n <= 1; n++)
                    {
                        DAA_TABLE[(c << 8) | (n << 9) | (h << 10) | i] = (short) getDAAResult(i, c | (n << 1) | (h << 4)); 
                    }
                }
            }
        }
        
        // Reset these to be sure
        a = f = 0;
    }
    
    private int getDAAResult(int value, int flags)
    {
        a = value;
        f = flags;
        
        int a_copy = a;
        int correction = 0;
        int carry = (flags & F_CARRY);
        int carry_copy = carry;
        if (((flags & F_HALFCARRY) != 0) || ((a_copy & 0x0f) > 0x09))
        {
            correction |= 0x06;
        }
        if ((carry == 1) || (a_copy > 0x9f) || ((a_copy > 0x8f) && ((a_copy & 0x0f) > 0x09)))
        {
            correction |= 0x60;
            carry_copy = 1;
        }
        if (a_copy > 0x99)
        {
            carry_copy = 1;
        }
        if ((flags & F_NEGATIVE) != 0)
        {
            // cycle -= 4;
            sub_a(correction);
        }
        else
        {
            // cycle -= 4;
            add_a(correction);
        }

        flags = (f & 0xfe) | carry_copy;

        if (getParity(a))
        {
            flags = (flags & 0xfb) | F_PARITY;
        }
        else
        {
            flags = (flags & 0xfb);
        }   
         
        return a | (flags << 8);            
    }

    // --------------------------------------------------------------------------------------------
    // ACCUMULATOR REGISTER
    // --------------------------------------------------------------------------------------------

    /**
     *  ADD 8 BIT
     *
     *  @param value        Value to add
     */

    private final void add_a(int value)
    {
        int temp = (a + value) & 0xff;
        f = SZHVC_ADD_TABLE[ (a<<8) | temp];
        a = temp;
    }

    /**
     *  ADC 8 BIT - Add with carry
     *
     *  @param value        Value to add
     */

    private final void adc_a(int value)
    {
        int carry = f & F_CARRY;
        int temp = (a + value + carry) & 0xff;
        f = SZHVC_ADD_TABLE[ (carry<<16) | (a<<8) | temp];
        a = temp;
    }

    /**
     *  SUB 8 BIT
     *
     *  @param value        Value to subtract
     */

    private final void sub_a(int value)
    {
        int temp = (a - value) & 0xff;
        f = SZHVC_SUB_TABLE[ (a<<8) | temp];
        a = temp;
    }

    /**
     *  SBC 8 BIT
     *
     *  @param value        Subtract with carry
     */

    private final void sbc_a(int value)
    {
        int carry = f & F_CARRY;
        int temp = (a - value - carry) & 0xff;
        f = SZHVC_SUB_TABLE[ (carry<<16) | (a<<8) | temp];
        a = temp;
    }

    /**
     *  AND Operation
     *
     *  @param value        Value to &
     */

    //private final void and_a(int value)
    //{
    //    f = SZP_TABLE[a &= value] | F_HALFCARRY;
    //}

    /**
     *  OR Operation (bitwise inclusive OR to turn relevant bits on)
     *
     *  @param value        Value to |
     */

    //private final void or_a(int value)
    //{  
    //    f = SZP_TABLE[a |= value];
    //}

    /**
     *  XOR Operation (Bitwise Exclusive OR)
     *
     *  @param value        Value to ^
     */

    //private final void xor_a(int value)
    //{
    //    f = SZP_TABLE[a ^= value];
    //}


    /**
     *  CP Operation - Compare with Accumulator
     *
     *  @param value        Value to compare
     */

    private final void cp_a(int value)
    {
        // Subtract value from accumulator but discard result
        f = SZHVC_SUB_TABLE[(a<<8) | ((a - value) & 0xff)];
    }

    /**
     *  CPL Operation - Complement Accumulator
     *
     *  Bit 3 and Bit incomplete
     */

    private final void cpl_a()
    {
        a ^= 0xFF;
        f |= (F_NEGATIVE | F_HALFCARRY);
    }

    /**
     *  RRA Operation - Rotate Right Accumulator
     */

    private final void rra_a()
    {
        int carry = a & 1; // bit 1 rotates to carry flag
        a = ((a>>1) | (f & F_CARRY)<<7)&0xff; // Shift Right One Bit Position
        f = (f & 0xec) | carry; 
    }

    /**
     *  RLA Operation - Rotate Left Accumulator
     */

    private final void rla_a()
    {
        int carry = a >> 7; // bit 7 rotates to carry flag
        a = ( (a<<1) | (f & F_CARRY) ) &0xff;
        f = (f & 0xec) | carry; 
    }

    /**
     *  RLCA Operation - Rotate Left With Carry Accumulator
     */

    private final void rlca_a()
    {
        // Transfer Original Bit 7 to Bit 0 and Carry Flag
        int carry = a >> 7;

        // Shift register left
        a = ((a << 1) & 0xff) | carry;
        
        // Retain Sign, Zero, Bit 5, Bit 3 and Parity
        f = (f & 0xec) | carry; 
    }


    /**
     *  RRCA Operation - Rotate Right With Carry Accumulator
     */

    private final void rrca_a()
    {
        int carry = a & 1;
        
        a = (a >> 1) | (carry << 7);
        
        // Retain Sign, Zero, Bit 5, Bit 3 and Parity
        f = (f & 0xec) | carry; 
    }
    
    // --------------------------------------------------------------------------------------------
    // NORMAL REGISTER ACCESS
    // --------------------------------------------------------------------------------------------
        
    private final int getBC()
    {
        return (b << 8) | c;
    }
    
    private final int getDE()
    {
        return (d << 8) | e;
    }
    
    private final int getHL()
    {
        return (h << 8) | l;
    }
    
    private final int getIX()
    {
        return (ixH << 8) | ixL;
    }
    
    private final int getIY()
    {
        return (iyH << 8) | iyL;
    }
    
    private final void setBC(int value)
    {
        b  = (value >> 8);
        c  = value & 0xff;
    }
    
    private final void setDE(int value)
    {
        d  = (value >> 8);
        e  = value & 0xff;
    }
    
    private final void setHL(int value)
    {
        h  = (value >> 8);
        l  = value & 0xff;
    }
    
    private final void setIX(int value)
    {
        ixH  = (value >> 8);
        ixL  = value & 0xff;
    }
    
    private final void setIY(int value)
    {
        iyH  = (value >> 8);
        iyL  = value & 0xff;
    }
    
    private final void incBC()
    {
        c = (c + 1)&0xff;
        if (c == 0) b = (b + 1)&0xff;
    }
    
    private final void incDE()
    {
        e = (e + 1)&0xff;
        if (e == 0) d = (d + 1)&0xff;
    }
    
    private final void incHL()
    {
        l = (l + 1)&0xff;
        if (l == 0) h = (h + 1)&0xff;
    }
    
    private final void incIX()
    {
        ixL = (ixL + 1)&0xff;
        if (ixL == 0) ixH = (ixH + 1)&0xff;
    }
    
    private final void incIY()
    {
        iyL = (iyL + 1)&0xff;
        if (iyL == 0) iyH = (iyH + 1)&0xff;
    }
    
    private final void decBC()
    {
        c = (c - 1)&0xff;
        if (c == 255) b = (b - 1)&0xff;
    }
    
    private final void decDE()
    {
        e = (e - 1)&0xff;
        if (e == 255) d = (d - 1)&0xff;
    }
    
    private final void decHL()
    {
        l = (l - 1)&0xff;
        if (l == 255) h = (h - 1)&0xff;
    }
    
    private final void decIX()
    {
        ixL = (ixL - 1)&0xff;
        if (ixL == 255) ixH = (ixH - 1)&0xff;
    }
    
    private final void decIY()
    {
        iyL = (iyL - 1)&0xff;
        if (iyL == 255) iyH = (iyH - 1)&0xff;
    }
    
    private final int inc8(int value)
    {
        value = (value + 1) & 0xff;
        f = (f & F_CARRY) | SZHV_INC_TABLE[value];
        return value;
    }
    
    private final int dec8(int value)
    {
        value = (value - 1) & 0xff;
        f = (f & F_CARRY) | SZHV_DEC_TABLE[value];
        return value;
    }
    
    // --------------------------------------------------------------------------------------------
    // EXCHANGE REGISTER BANKS
    // --------------------------------------------------------------------------------------------

    private final void exAF()
    {
        int temp = a; a = a2; a2 = temp;        
        temp = f; f = f2; f2 = temp;
    }
    
    private final void exBC()
    {
        int temp = b; b = b2; b2 = temp;
        temp = c; c = c2; c2 = temp;
    }
    
    private final void exDE()
    {
        int temp = d; d = d2; d2 = temp;
        temp = e; e = e2; e2 = temp;
    }
    
    private final void exHL()
    {
        int temp = h; h = h2; h2 = temp;
        temp = l; l = l2; l2 = temp;
    }
    
    
    
    private final int add16(int reg, int value)
    {
        int result = reg + value;
        f = (f & 0xc4) | (((reg ^ result ^ value) >> 8) & 0x10) | ((result >> 16) & 1);
        return (result & 0xffff);
    }

    /**
     * Add with carry (16-bit)
     * 
     * Only ever affects HL register
     * 
     * @param value
     */
    private final void adc16(int value)
    {
         int hl = (h << 8) | l;
         
         int result = hl + value + (f & F_CARRY);
         f = (((hl ^ result ^ value) >> 8) & 0x10) | ((result >> 16) & 1) | ((result >> 8) & 0x80) | (((result & 0xffff)!=0) ? 0 : 0x40) | (((value ^ hl ^ 0x8000) & (value ^ result) & 0x8000) >> 13);
         h = (result >> 8) & 0xff;
         l = result & 0xff;
    }
    
    /**
     * Subtract with carry (16-bit)
     * 
     * Only ever affects HL register
     * 
     * @param value
     */ 
    private final void sbc16(int value)
    {
        int hl = (h << 8) | l;

        int result = hl - value - (f & F_CARRY);
        f = (((hl ^ result ^ value) >> 8) & 0x10) | 0x02 | ((result >> 16) & 1) | ((result >> 8) & 0x80) | (((result & 0xffff) != 0) ? 0 : 0x40) | (((value ^ hl) & (hl ^ result) &0x8000) >> 13);
        h = (result >> 8) & 0xff;
        l = result & 0xff;
    }

    
    /**
     * Increment Refresh register
     */
    
    private final void incR()
    {
        r = (r & 0x80) | ((r+1) & 0x7F);
    }
    
    // --------------------------------------------------------------------------------------------
    // FLAG REGISTER
    // --------------------------------------------------------------------------------------------
        
    /**
     * Generate flag tables
     * 
     * Based on code from the Java Emulation Framework
     * Copyright (C) 2002 Erik Duijs (erikduijs@yahoo.com)
     */

    private void generateFlagTables()
    {
        SZ_TABLE = new short[256];
        SZP_TABLE = new short[256];
        SZHV_INC_TABLE = new short[256];
        SZHV_DEC_TABLE = new short[256];
        SZ_BIT_TABLE = new short[256];

        // Generate tables
        for ( int i = 0; i < 256; i++ ) 
        {
            // Sign bits (0x80)
            int sf = ((i & 0x80) != 0 ? F_SIGN : 0);
                        
            // Zero bits (0x40)
            int zf = (i == 0 ? F_ZERO : 0);
                    
            // Bit 5 (0x20)
            int yf = i & 0x20;
                
            // Halfcarry (0x10)
            //int hf = 0;
                
            // Bit 3 (0x08)
            int xf = i & 0x08;
                
            // Overflow (0x04)
            //int vf = 0;
            
            // Parity bits (0x04)
            int pf = ( getParity(i) ? F_PARITY : 0);
            
            // Generate Sign/Zero Table
            SZ_TABLE[i] = (short) (sf | zf | yf | xf);
            
            // Generate Sign/Zero/Parity Table
            SZP_TABLE[i]      = (short) (sf | zf | yf | xf | pf);
            
            // Generate table for inc8 instruction
            SZHV_INC_TABLE[i] = (short) (sf | zf | yf | xf);  
            SZHV_INC_TABLE[i] |= (i == 0x80) ? F_OVERFLOW : 0;
            SZHV_INC_TABLE[i] |= ((i & 0x0f) == 0x00) ? F_HALFCARRY : 0;
            
            // Generate table for dec8 instruction
            SZHV_DEC_TABLE[i] = (short) (sf | zf | yf | xf | F_NEGATIVE);  
            SZHV_DEC_TABLE[i] |= (i == 0x7F) ? F_OVERFLOW : 0;
            SZHV_DEC_TABLE[i] |= ((i & 0x0f) == 0x0F) ? F_HALFCARRY : 0;    
            
            // Generate table for bit instruction (set sign flag on here)
            SZ_BIT_TABLE[i] = (short) ((i != 0) ? i & 0x80 : F_ZERO | F_PARITY);      
            SZ_BIT_TABLE[i] |= yf | xf | F_HALFCARRY; // halfcarry is always on with bit instruction :)
        }
        
        // ----------------------------------------------------------------------------------------
        // Generate fast lookups for ADD/SUB/ADC/SBC instructions
        // ----------------------------------------------------------------------------------------
        
        SZHVC_ADD_TABLE = new short[2*256*256];
        SZHVC_SUB_TABLE = new short[2*256*256];
        
        int padd = 0*256;
        int padc = 256*256;
        int psub = 0*256;
        int psbc = 256*256;
        
        for (int oldval = 0; oldval < 256; oldval++)
        {
            for (int newval = 0; newval < 256; newval++)
            {
                /* add or adc w/o carry set */
                int val = newval - oldval;

                if (newval != 0)
                {
                    if ((newval & 0x80) != 0)
                    {
                        SZHVC_ADD_TABLE[padd] = F_SIGN;
                    }
                    else
                    {
                        SZHVC_ADD_TABLE[padd] = 0;
                    }
                }
                else
                {
                    SZHVC_ADD_TABLE[padd] = F_ZERO;
                }

                SZHVC_ADD_TABLE[padd] |= (newval & (F_BIT5 | F_BIT3)); /* undocumented flag bits 5+3 */

                if ((newval & 0x0f) < (oldval & 0x0f))
                {
                    SZHVC_ADD_TABLE[padd] |= F_HALFCARRY;
                }
                if (newval < oldval)
                {
                    SZHVC_ADD_TABLE[padd] |= F_CARRY;
                }
                if (((val ^ oldval ^ 0x80) & (val ^ newval) & 0x80) != 0)
                {
                    SZHVC_ADD_TABLE[padd] |= F_OVERFLOW;
                }
                padd++;

                /* adc with carry set */
                val = newval - oldval - 1;
                if (newval != 0)
                {
                    if ((newval & 0x80) != 0)
                    {
                        SZHVC_ADD_TABLE[padc] = F_SIGN;
                    }
                    else
                    {
                        SZHVC_ADD_TABLE[padc] = 0;
                    }
                }
                else
                {
                    SZHVC_ADD_TABLE[padc] = F_ZERO;
                }

                SZHVC_ADD_TABLE[padc] |= (newval & (F_BIT5 | F_BIT3)); /* undocumented flag bits 5+3 */
                if ((newval & 0x0f) <= (oldval & 0x0f))
                {
                    SZHVC_ADD_TABLE[padc] |= F_HALFCARRY;
                }
                if (newval <= oldval)
                {
                    SZHVC_ADD_TABLE[padc] |= F_CARRY;
                }
                if (((val ^ oldval ^ 0x80) & (val ^ newval) & 0x80) != 0)
                {
                    SZHVC_ADD_TABLE[padc] |= F_OVERFLOW;
                }
                padc++;

                /* cp, sub or sbc w/o carry set */
                val = oldval - newval;
                if (newval != 0)
                {
                    if ((newval & 0x80) != 0)
                    {
                        SZHVC_SUB_TABLE[psub] = F_NEGATIVE | F_SIGN;
                    }
                    else
                    {
                        SZHVC_SUB_TABLE[psub] = F_NEGATIVE;
                    }
                }
                else
                {
                    SZHVC_SUB_TABLE[psub] = F_NEGATIVE | F_ZERO;
                }

                SZHVC_SUB_TABLE[psub] |= (newval & (F_BIT5 | F_BIT3)); /* undocumented flag bits 5+3 */
                if ((newval & 0x0f) > (oldval & 0x0f))
                {
                    SZHVC_SUB_TABLE[psub] |= F_HALFCARRY;
                }
                if (newval > oldval)
                {
                    SZHVC_SUB_TABLE[psub] |= F_CARRY;
                }
                if (((val ^ oldval) & (oldval ^ newval) & 0x80) != 0)
                {
                    SZHVC_SUB_TABLE[psub] |= F_OVERFLOW;
                }
                psub++;

                /* sbc with carry set */
                val = oldval - newval - 1;
                if (newval != 0)
                {
                    if ((newval & 0x80) != 0)
                    {
                        SZHVC_SUB_TABLE[psbc] = F_NEGATIVE | F_SIGN;
                    }
                    else
                    {
                        SZHVC_SUB_TABLE[psbc] = F_NEGATIVE;
                    }
                }
                else
                {
                    SZHVC_SUB_TABLE[psbc] = F_NEGATIVE | F_ZERO;
                }

                SZHVC_SUB_TABLE[psbc] |= (newval & (F_BIT5 | F_BIT3)); /* undocumented flag bits 5+3 */
                if ((newval & 0x0f) >= (oldval & 0x0f))
                {
                    SZHVC_SUB_TABLE[psbc] |= F_HALFCARRY;
                }
                if (newval >= oldval)
                {
                    SZHVC_SUB_TABLE[psbc] |= F_CARRY;
                }
                if (((val ^ oldval) & (oldval ^ newval) & 0x80) != 0)
                {
                    SZHVC_SUB_TABLE[psbc] |= F_OVERFLOW;
                }
                psbc++;
            }
        }
    }
    
    /**
     * Return the parity of a number.
     * Only used for pre-calculations.
     * 
     * @param   value
     * @return  true if parity
     */
    private boolean getParity(int value)
    {
        boolean parity = true;
        for (int j = 0; j < 8; j++)
        {
            if ((value & (1 << j)) != 0)
            {
                parity = !parity;
            }
        }
        return parity;
    }
    
    // --------------------------------------------------------------------------------------------
    // MEMORY ACCESS
    //
    // Simply moved here for speed purposes
    // --------------------------------------------------------------------------------------------
    
    /** Cartridge ROM pages */
    private byte [][] rom;
    
    /** RAM */
    public byte [][] ram;
    
    /** SRAM */
    public byte [][] sram;
    
    /** Catridge uses SRAM */
    private boolean useSRAM;
    
    /** Memory frame registers */
    public int[] frameReg = new int[4];
    
    /** Total number of 16K catridge pages */
    private int number_of_pages;
    
    /** Memory map */
    private byte[][] memWriteMap, memReadMap;
    
    /** Dummy memory writes (never read) */
    private byte[] dummyWrite;
    

    /**
     *  Memory Constructor.
     */

    private void generateMemory()
    {
        // Create read/write memory map (64 positions each representing 1K)
        
        // Note we create one extra dummy position to get around a dodgy write in
        // Back to the Future 2.        
        memReadMap = new byte[65][];
        memWriteMap = new byte[65][];
        
        // Create 8K System RAM
        ram = new byte[8][Setup.PAGE_SIZE];
        
        // Create 2 x 16K RAM Cartridge Pages
        if (sram == null)
        {
            sram = new byte[32][Setup.PAGE_SIZE];
            useSRAM = false;
        }
        
        // Create dummy memory (for invalid writes)
        dummyWrite = new byte[Setup.PAGE_SIZE];
        
        // Ignore bad writes in Back To The Future 2
        memReadMap[64] = dummyWrite;
        memWriteMap[64] = dummyWrite;
        
        number_of_pages = 2;
    }

    /**
     *  Reset Memory to Default Values.
     */

    public void resetMemory(byte[][] p)
    {
        if (p != null) rom = p;
        
        frameReg[0] = 0; 
        frameReg[1] = 0;
        frameReg[2] = 1;
        frameReg[3] = 0;
        
        // Default Mapping
        if (rom != null)
        {
            // 16K Page Chunks :)
            number_of_pages = rom.length / 16;            
            setDefaultMemoryMapping();
        }
        else
            number_of_pages = 0;   
    }
    
    private void setDefaultMemoryMapping()
    {
        // Map ROM
        for (i = 0; i < 48; i++)
        {
            memReadMap[i]  = rom[i & 31];
            memWriteMap[i] = dummyWrite;
        }

        // Map RAM
        for (i = 48; i < 64; i++)
        {
            memReadMap[i] = ram[i & 7];
            memWriteMap[i] = ram[i & 7];
        }    
    }

    /**
     *  Write to a memory location.
     *
     *  @param address      Memory address
     *  @param value        Value to write
     */
    
    private final void writeMem(int address, int value)
    {
        memWriteMap[address >> 10][address & 0x3FF] = (byte) value;
        
        // Paging registers
        if (address >= 0xFFFC)
            page(address & 3, value);
    }
    
    /**
     *  Read from a memory location.
     *
     *  @param location     Memory location
     *
     *  @return             Value from memory location
     */

    private final int readMem(int address)
    {
        return memReadMap[address >> 10][address & 0x3FF] & 0xFF;
    }

    
    /**
     *  Read a signed value from next memory location
     *
     *  @return             Value from memory location
     */

    private final int d()
    {        
        return memReadMap[pc >> 10][pc & 0x3FF];
    }

    /**
     *  Read a word (two bytes) from a memory location.
     *
     *  @param address      Memory address
     *
     *  @return             Value from memory location
     */

    private final int readMemWord(int address)
    {
        return (memReadMap[address >> 10][address & 0x3FF] & 0xFF) | 
                ((memReadMap[++address >> 10][address & 0x3FF] & 0xFF) << 8);
    }

    /**
     *  Write to a paging register
     *
     *  $FFFC - Control register
     *  
     *  D7 : 1= /GWR disabled (write protect), 0= /GWR enabled (write enable)
     *  D4 : 1= SRAM mapped to $C000-$FFFF (*1)
     *  D3 : 1= SRAM mapped to $8000-$BFFF, 0= ROM mapped to $8000-$BFFF
     *  D2 : SRAM banking; BA14 state when $8000-$BFFF is accessed (1= high, 0= low) 
     *  D1 : Bank shift, bit 1
     *  D0 : Bank shift, bit 0
     *
     *  @param location     Memory location
     *  @param value        Value to write
     */

    private final void page(int address, int value)
    {   
        frameReg[address] = value;
        
        switch (address)
        {
            // 0xFFFC: RAM/ROM select register
            case 0:
                // 1= SRAM mapped to $8000-$BFFF
                if ((value & 0x08) != 0)
                {  
                    // SRAM banking; BA14 state when $8000-$BFFF is accessed (1= high, 0= low) 
                    
                    // 16K offset into SRAM
                    int offset = (value & 0x04) << 2;
                    
                    // Map 16K of SRAM
                    for (int i = 32; i < 48; i++)
                        memReadMap[i] = memWriteMap[i] = sram[offset++];
                    
                    useSRAM = true;
                }
                // 0= ROM mapped to $8000-$BFFF
                else
                {
                    int p = (frameReg[3] % number_of_pages) << 4;
                    
                    // Map 16K of ROM
                    for (int i = 32; i < 48; i++)
                    {
                        memReadMap[i] = rom[p++];
                        memWriteMap[i] = dummyWrite;
                    }
                }
                break;
                
            // 0xFFFD: Page 0 ROM Bank
            case 1:
            {
                // Note +1 here, because for loop starts at '1'
                int p = ((value % number_of_pages) << 4) + 1;
                
                for (int i = 1; i < 16; i++)
                    memReadMap[i] = rom[p++];
            }
                break;
                
            // 0xFFFE: Page 1 ROM Bank
            case 2:       
            {
                int p = (value % number_of_pages) << 4;
                
                for (int i = 16; i < 32; i++)
                    memReadMap[i] = rom[p++];
            }
                break;
                
            // 0xFFFF: Page 2 ROM Bank
            case 3:
                // Map ROM
                if ((frameReg[0] & 0x08) == 0)
                {
                    int p = (value % number_of_pages) << 4;

                    for (int i = 32; i < 48; i++)
                        memReadMap[i] = rom[p++];
                }                                    
                break;
        }      
    }
    
    public boolean hasUsedSRAM()
    {
        return useSRAM;
    }
    
    public void setSRAM(byte[] bytes)
    {
        int length = bytes.length / Setup.PAGE_SIZE;
        
        for (int i = 0; i < length; i++)
            System.arraycopy(bytes, i * Setup.PAGE_SIZE, sram[i], 0, Setup.PAGE_SIZE);
    }
    

    /**
     * Called when restoring from a saved state
     * 
     * @param state     Contents of frame register
     */
    public void setStateMem(int[] state)
    {
        frameReg = state;
         
        setDefaultMemoryMapping();
        
        page(3, frameReg[3]);
        page(2, frameReg[2]);
        page(1, frameReg[1]);
        page(0, frameReg[0]);
    }
    
    // --------------------------------------------------------------------------------------------
    // Z80 State Saving
    // --------------------------------------------------------------------------------------------
    
    /** Length of state array */
    public final static int STATE_LENGTH = 8;
    
    public int[] getState()
    {
        int state[] = new int[STATE_LENGTH];
        
        state[0] = pc | (sp << 16);
        state[1] = (iff1 ? 0x01 : 0) | (iff2 ? 0x02 : 0) | (halt ? 0x04 : 0) | (EI_inst ? 0x08 : 0) | (interruptLine ? 0x10 : 0);
        state[2] = a | (a2 << 8) | (f << 16) | (f2 << 24); // AF AF'
        state[3] = getBC() | (getDE() << 16); // BC DE
        state[4] = getHL() | (r << 16) | (i << 24); // HL, r, i
        state[5] = getIX() | (getIY() << 16); // IX, IY
        
        exBC(); exDE(); exHL(); // swap registers
        
        state[6] = getBC() | (getDE() << 16); // BC' DE'
        state[7] = getHL() | (im << 16) | (interruptVector << 24); // HL' and interrupt mode
        
        exBC(); exDE(); exHL(); // restore registers

        return state;
    }
    
    public void setState(int[] state)
    {
        int temp = state[0];
        pc = temp & 0xFFFF;
        sp = (temp >> 16) & 0xFFFF;
        
        temp = state[1];
        iff1 = (temp & 0x01) != 0;
        iff2 = (temp & 0x02) != 0;
        halt = (temp & 0x04) != 0;
        EI_inst = (temp & 0x08) != 0;
        interruptLine = (temp & 0x10) != 0;
        
        temp = state[2];
        a = temp & 0xFF;
        a2 = (temp >> 8) & 0xFF;
        f = (temp >> 16) & 0xFF;
        f2 = (temp >> 24) & 0xFF;
        
        temp = state[3];
        setBC(temp & 0xFFFF);
        setDE((temp >> 16) & 0xFFFF);
        
        temp = state[4];
        setHL(temp & 0xFFFF);
        r = (temp >> 16) & 0xFF;
        i = (temp >> 24) & 0xFF;
        
        temp = state[5];
        setIX(temp & 0xFFFF);
        setIY((temp >> 16) & 0xFFFF);
        
        exBC(); exDE(); exHL(); // swap registers
        
        temp = state[6];
        setBC(temp & 0xFFFF);
        setDE((temp >> 16) & 0xFFFF);
        
        temp = state[7];
        setHL(temp & 0xFFFF);
        im = (temp >> 16) & 0xFF;
        interruptVector = (temp >> 24) & 0xFF;
        
        exBC(); exDE(); exHL(); // restore registers
    }
}