/*
 ** 2011 August 20
 **
 ** The author disclaims copyright to this source code.  In place of
 ** a legal notice, here is a blessing:
 **    May you do good and not evil.
 **    May you find forgiveness for yourself and forgive others.
 **    May you share freely, never taking more than you give.
 */
package info.ata4.bsplib.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 * Utility class to open memory-mapped files.
 * 
 * @author Nico Bergemann <barracuda415 at yahoo.de>
 */
public class MappedFileUtils {
    
    public static ByteBuffer openReadOnly(File file) throws IOException {
        ByteBuffer bb;
        FileInputStream fis = null;
        
        try {
            // open input stream
            fis = FileUtils.openInputStream(file);
            // get file channel
            FileChannel fc = fis.getChannel();
            // map entire file as byte buffer
            bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
        } finally {
            // close file stream, the byte buffer will remain active
            IOUtils.closeQuietly(fis);
        }
        
        return bb;
    }
    
    public static ByteBuffer openReadWrite(File file, int size) throws IOException {
        ByteBuffer bb;
        RandomAccessFile raf = null;
        
        try {
            // open random access file
            raf = new RandomAccessFile(file, "rw");
            // reset file
            raf.setLength(0);
            // get file channel
            FileChannel fc = raf.getChannel();
            // map file as byte buffer
            bb = fc.map(FileChannel.MapMode.READ_WRITE, 0, size);
        } finally {
            // close file, the byte buffer will remain active
            IOUtils.closeQuietly(raf);
        }
        
        return bb;
    }
}
