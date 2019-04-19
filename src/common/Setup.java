/**
 * Setup.java
 *
 * Global System Settings
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

public final class Setup
{   
    public static final String PROGRAM_NAME  = "JavaGear";
    public static final String AUTHOR  = "Chris White";  
    
    // --------------------------------------------------------------------------------------------
    // Debug settings
    // --------------------------------------------------------------------------------------------
    
    /** Print timing information on screen */
    public final static boolean DEBUG_TIMING = false;
        
    // --------------------------------------------------------------------------------------------
    // CPU Settings
    // --------------------------------------------------------------------------------------------
    
    /** Refresh register emulation (not required by any games?) */
    public final static boolean REFRESH_EMULATION = false;
    
    /*
     * Games requiring accurate interrupt emulation:
     * 
     * Earthworm Jim (GG)
     */
    
    /** Do accurate interrupt emulation? (slower!) */
    public final static boolean ACCURATE_INTERRUPT_EMULATION = BuildSettings.ACCURATE;
    
    // --------------------------------------------------------------------------------------------
    // Lightgun Mode (For the following titles)
    //
    //  Assault City
    //  Gangster Town
    //  Laser Ghost
    //  Marksman Shooting / Trap Shooting / Safari Hunt
    //  Missile Defense 3D
    //  Operation Wolf
    //  Rambo III
    //  Rescue Mission
    //  Shooting Gallery
    //  Space Gun
    //  Wanted
    // --------------------------------------------------------------------------------------------
    
    public final static boolean LIGHTGUN = BuildSettings.ACCURATE;
    
    // --------------------------------------------------------------------------------------------
    // VDP Settings
    // --------------------------------------------------------------------------------------------
    
    /* 
       Games requiring sprite collision:
        + Cheese Cat'astrophe (SMS)
        + Ecco the Dolphin (SMS, GG)
        + Fantastic Dizzy (SMS, GG)
        + Fantazy Zone Gear (GG)
        + Impossible Mission (SMS)
        + Taz-Mania (SMS, GG)
    */
    
    /** Emulate hardware sprite collisions (not used by many games, and slower) */
    public final static boolean VDP_SPRITE_COLLISIONS = BuildSettings.ACCURATE;
        
    // --------------------------------------------------------------------------------------------
    // Memory Settings
    // --------------------------------------------------------------------------------------------
    
    /** Size of each memory page (1K in this case) */
    public final static int PAGE_SIZE = 0x400;
}