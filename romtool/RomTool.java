import java.io.*;
import java.util.*;
import java.util.zip.CRC32;
import java.util.jar.*;

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

public class RomTool
{
    // --------------------------------------------------------------------------------------------
    // Program Information
    // --------------------------------------------------------------------------------------------

    private final static String PROGRAM_INFO = "JavaGear ROM Tool";
    private final static String VERSION = "0.11b";

    // --------------------------------------------------------------------------------------------
    // Data files required by program
    // --------------------------------------------------------------------------------------------

    private final static String SMS_ROMDATA = "Master System.romdata";
    private final static String GG_ROMDATA = "Game Gear.romdata";
    
    // --------------------------------------------------------------------------------------------
    // Other stuff
    // --------------------------------------------------------------------------------------------
    
    /** Config file to store rom data */
    private final static String CONFIG_FILE = "javagear.conf";
    
    /** Information on all roms, stored by CRC32 */
    private static Hashtable smsData = new Hashtable();
    private static Hashtable ggData = new Hashtable();
    
    /** SMS/GG mode based on file extension */
    private static int mode;
    
    /** Midlet name, updated from rom name */
    private static String midletName;
    
    /** Name of cartridge */
    private static String romName;
    
    private final static int 
        MODE_SMS = 1,
        MODE_GG = 2;
    
    // --------------------------------------------------------------------------------------------

    public static void main(String[] args)
    {
        System.out.println(PROGRAM_INFO + " (v"+VERSION+")");
        System.out.println();

        if (args.length < 2)
        {
            System.out.println("Usage: RomTool <romfile> <jadfile/jarfile>");
            System.exit(-1);
        }
        else
        {
            boolean isJad = args[1].toLowerCase().endsWith(".jad");
            
            File romFile = new File(args[0]);
            File jadFile = new File(args[1]);
            
            // Detect SMS or GG ROM       
            String romName = args[0].toLowerCase();
            if (romName.endsWith(".sms")) mode = MODE_SMS;
            else if (romName.endsWith(".gg")) mode = MODE_GG;
            else
            {
                System.out.println("Error: Rom name must end with .sms or .gg");
                System.exit(-1);
            }
           
            System.out.print("Reading data files... ");
            smsData = readRomDataFile(new File(SMS_ROMDATA));
            ggData = readRomDataFile(new File(GG_ROMDATA));
            System.out.println("done");
            
            // ------------------------------------------------------------------------------------
            // USING JAD FILE (Probably MIDP)
            // ------------------------------------------------------------------------------------
            if (isJad)
            {
                ArrayList jadContents = getTextContents(jadFile);
                String jarName = getJadValue(jadContents, "MIDlet-Jar-URL");
                
                if (jarName == null)
                {
                    System.out.println("Could not read name of JAR file from JAD");
                    System.exit(-1);
                }

                // Read ROM
                readRom(romFile);
                
                // Update JAR and JAD with ROM and new details
                try
                {
                    File jarFile = new File(jadFile.getParent(), jarName);
                    
                    updateJAR(jarFile, romFile);
                    updateJAD(jadFile, jadContents, (int) jarFile.length());
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    System.exit(-1);
                }            
            }
            // ------------------------------------------------------------------------------------
            // USING JAR FILE (J2SE or MIDP)
            // ------------------------------------------------------------------------------------
            else
            {
                // Read ROM
                readRom(romFile);   
                updateJAR(jadFile, romFile);
            }
        }
    }
    
    private static void readRom(File file)
    {
        try
        {
            System.out.print("Reading rom file... ");
            
            if (!file.exists())
            {
                System.out.println("File does not exist");
                System.exit(-1);               
            }
            
            int size = (int) file.length();
                         
            // Don't load games smaller than 16K
            if (size < 0x4000)
            {
                System.out.println("Can't load games smaller than 16K");
                System.exit(-1);
            }

            // Read ROM to byte array
            FileInputStream bis = new FileInputStream(file);       
            byte [] data = new byte[size];
            bis.read(data);            
            bis.close(); 
            
            System.out.println("done");
            
            // Calculate ROM CRC value
            CRC32 crc = new CRC32();
            crc.update(data);
            String romCRC = Long.toHexString(crc.getValue()).toUpperCase();
            
            // Establish identity of ROM
            String[] romInfo = null;
            if (mode == MODE_SMS) romInfo = (String[]) smsData.get(romCRC);
            else if (mode == MODE_GG) romInfo = (String[]) ggData.get(romCRC);
            
            String romName = "Unknown";
                              
            System.out.println("---------------------------------------------");
            if (romInfo != null)
            {
                romName = romInfo[2].trim();
                String notes = getNotes((romInfo[1].toCharArray())[0]);
                    
                // Print rom Name
                System.out.println("Detected: "+romName);
                System.out.println("CRC     : "+romCRC);
                System.out.println("Size    : "+(size / 1024)+"K");
                System.out.println("Notes   : "+notes);           
            }
            else
            {
                System.out.println("ROM details not in database. May not work.");
            }
            System.out.println("---------------------------------------------");
        
            // Update midlet name
            midletName = romName.substring(0, Math.min(18, romName.length())).trim();
            
            System.out.print("Writing config file... ");
            writeConfigFile(romName, file.getName(), size);
            System.out.println("done");
        }
        catch (IOException e)
        {
            System.out.println("Error reading rom");
            e.printStackTrace();
            System.exit(-1);
        }     
    }
    
    private static void writeConfigFile(String romName, String fileName, int size)
    {      
        try
        {
            DataOutputStream dos = new DataOutputStream(new FileOutputStream(new File(CONFIG_FILE)));
            
            dos.writeUTF(romName);
            dos.writeUTF(fileName);
            dos.writeInt(size);
            dos.writeByte(mode);
            
            dos.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            System.exit(-1);
        }
    }
    
    private static String getNotes(char c)
    {
        switch (c)
        {
            case 'B': return "Warning -- Bad Bytes in ROM. May not work.";              
            case 'D': return "Demo / Homebrew";   
            case 'E': return "Warning -- Wrong File Extension. May not work.";
            case 'F': return "Variant";               
            case 'H': return "Trained version";            
            case 'I': return "Bios";               
            case 'O': return "Overdump";           
            case 'T': return "Translation";               
            case 'V': return "Warning -- Corrupted ROM. May not work.";
            default:  return "None";
        }
    }

    private static Hashtable readRomDataFile(File file)
    {        
        Hashtable romData = new Hashtable();
        ArrayList lines = getTextContents(file);
        
        for (int i = 0; i < lines.size(); i++)
        {
            String thisLine = (String) lines.get(i);

            // Split on tab
            String[] splitLine = thisLine.split("\t");
            
            // Get CRC32 of this file
            String crcKey = splitLine[0].toUpperCase();
            
            // Add this to our hashtable of ROM info
            romData.put(crcKey, splitLine);         
        }
        return romData;
    }
    
    private static String getJadValue(ArrayList jadContents, String property)
    {
        for (int i = 0; i < jadContents.size(); i++)
        {
            String thisLine = (String) jadContents.get(i);

            // Split on colon
            String[] splitLine = thisLine.split(":");
            
            if (splitLine.length > 1)
            {
                if (splitLine[0].equals(property))
                    return splitLine[1].trim();
            }
        }
        return null;
    }

    /**
     * Fetch the entire contents of a text file, and return it in an ArrayList
     * This style of implementation does not throw Exceptions to the caller.
     * 
     * @param aFile is a file which already exists and can be read.
     */
    public static ArrayList getTextContents(File aFile)
    {
        // ...checks on aFile are elided
        ArrayList contents = new ArrayList();

        // declared here only to make visible to finally clause
        BufferedReader input = null;
        try
        {
            // use buffering, reading one line at a time
            // FileReader always assumes default encoding is OK!
            input = new BufferedReader(new FileReader(aFile));
            String line = null; // not declared within while loop
            /*
             * readLine is a bit quirky : it returns the content of a line MINUS the newline. it returns null only for the END of the stream. it returns an empty String if two newlines appear in a
             * row.
             */
            while ((line = input.readLine()) != null)
            {
                line = line.trim();
                
                // Ignore lines starting with '!" and blank lines
                if (!line.startsWith("!") && !line.equals(""))
                {
                    contents.add(line);
                }
            }
        }
        catch (FileNotFoundException ex)
        {
            ex.printStackTrace();
            System.exit(-1);
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            System.exit(-1);
        }
        finally
        {
            try
            {
                if (input != null)
                {
                    // flush and close both "input" and its underlying FileReader
                    input.close();
                }
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }
        return contents;
    }

    private static void updateJAD(File jadFile, ArrayList jadContents, int size)
    {
        System.out.print("Updating jad file... ");
        
        StringBuffer sb = new StringBuffer();
        
        // Update the JAR size listed in the JAD
        for (int i = 0; i < jadContents.size(); i++)
        {
            String thisLine = (String) jadContents.get(i);

            // Replace element with correct size
            if (thisLine.startsWith("MIDlet-Jar-Size"))
                sb.append("MIDlet-Jar-Size: "+size);
            
            // Update midlet name
            else if (thisLine.startsWith("MIDlet-Name"))
                sb.append("MIDlet-Name: "+midletName);
            
            // Update application name
            else if (thisLine.startsWith("MIDlet-1"))
            {
                sb.append("MIDlet-1: "+midletName+", i.png, JavaGear");
            }
            
            else
                sb.append(thisLine);
            
            sb.append( "\r\n" );
        }  
  
        // Write file
        try
        {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(jadFile));
            byte jadBytes[] = sb.toString().getBytes();

            for (int i = 0; i < jadBytes.length; i++)
                bos.write(jadBytes[i]);
            bos.flush();
            bos.close();
        }
        catch (Exception e)
        {
            System.out.println("Error writing JAD");
            e.printStackTrace();
            System.exit(-1);
        }
        
        System.out.println("done");      
    }
    
    private static void updateJAR(File jarFile, File romFile)
    {
        try
        {
            System.out.print("Updating jar file... ");
            File confFile = new File(CONFIG_FILE);
            
            File files[] = new File[2];
            files[0] = romFile;
            files[1] = confFile;
            
            addFilesToExistingJar(jarFile, files);
            
            // Delete config file on successful execution
            confFile.deleteOnExit();
            System.out.println("done");
        }
        catch (Exception e)
        {
            System.out.println("Error updating jar: " + jarFile.getName());
            e.printStackTrace();
            System.exit(-1);
        }
    }
    
    public static void addFilesToExistingJar(File zipFile, File[] files) throws IOException
    {
        // get a temp file
        File tempFile = File.createTempFile(zipFile.getName(), null);
        // delete it, otherwise you cannot rename your existing zip to it.
        tempFile.delete();

        boolean renameOk = zipFile.renameTo(tempFile);
        if (!renameOk)
        {
            throw new RuntimeException("could not rename the file " + zipFile.getAbsolutePath() + " to " + tempFile.getAbsolutePath());
        }
        byte[] buf = new byte[1024];

        JarInputStream jis = new JarInputStream(new FileInputStream(tempFile));
        
        // Rewrite the manifest
        Manifest manifest = jis.getManifest(); 

        // Change midlet name in manifest
        Attributes attribs = manifest.getMainAttributes();
        String midletAttrib = attribs.getValue("MIDlet-Name");
        
        if (midletAttrib != null)
            attribs.putValue("MIDlet-Name", midletName);  
  
        JarOutputStream out = new JarOutputStream(new FileOutputStream(zipFile), manifest);

        // Compress as much as possible
        out.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);

        JarEntry entry = jis.getNextJarEntry();
        while (entry != null)
        {
            String name = entry.getName();
            boolean notInFiles = true;
            
            // java 1.5
            //for (File f : files)
            
            for (int i = 0; i < files.length; i++)
            {
                File f = files[i];
              
                if (f.getName().equals(name) ||
                    name.endsWith(".gg") || name.endsWith(".sms")) // Don't re-copy ROM files to new file, in case user doesn't use fresh JAR
                {
                    notInFiles = false;
                    break;
                }
            }
            if (notInFiles)
            {
                // Add ZIP entry to output stream.
                out.putNextEntry(new JarEntry(name));
                // Transfer bytes from the ZIP file to the output file
                int len;
                while ((len = jis.read(buf)) > 0)
                {
                    out.write(buf, 0, len);
                }
            }
            entry = jis.getNextJarEntry();
        }
        // Close the streams
        jis.close();
               
        // Compress the files
        for (int i = 0; i < files.length; i++)
        {
            InputStream in = new FileInputStream(files[i]);
            // Add ZIP entry to output stream.
            out.putNextEntry(new JarEntry(files[i].getName()));
            // Transfer bytes from the file to the ZIP file
            int len;
            while ((len = in.read(buf)) > 0)
            {
                out.write(buf, 0, len);
            }
            // Complete the entry
            out.closeEntry();
            in.close();
        }
        // Complete the ZIP file
        out.close();
        tempFile.delete();
    }
}