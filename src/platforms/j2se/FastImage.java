/**
 * FastImage.java
 *
 * FastImage implements ImageProducer and is used to reconstruct the image whenever it is needed, 
 *
 * @author Copyright (C) 2002-2008 Chris White
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


import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Graphics;
import java.awt.image.*;


public class FastImage 
    implements ImageProducer
{
    /** Image Width */
    private int width;
    
    /** Image Height */
    private int height;
    
    /** Image Data */
    private Image image;
    
    /** Image Consumer */
    private ImageConsumer consumer;
    
    /** Direct Color Model (32 bits) */
    private DirectColorModel model;
    
    
    /**
     * Construct a FastImage
     * 
     * @param w Width of Image
     * @param h Height of Image
     */
    
    public FastImage(int w, int h)
    {
        width = w;
        height = h;
        
        model = new DirectColorModel(32,0x00FF0000,0x000FF00,0x000000FF,0);

        image = Toolkit.getDefaultToolkit().createImage(this);  
    }


    /**
     * Update the FastImage with new pixel data.
     * 
     * @param pixels     Pixel Array
     * @param offset     Offset Into Pixel Array
     * @param w          Width of pixel array
     * @param h          Height of pixel array
     * @param scanlength Spacing between rows
     */
    
    public void update(int[] pixels, int offset, int w, int h, int scanlength)
    {
        if (consumer != null)
        {
            // Copy Integer Pixel Data to ImageComsumer
            consumer.setPixels(0, 0, w, h, model, pixels, offset, scanlength);

            // Notify Image Consumer When Single Frame is Done
            consumer.imageComplete(ImageConsumer.SINGLEFRAMEDONE);
        }
    }
    
    
    /**
     * Paint the FastImage
     * 
     * @param g         Graphics context
     * @param x_offset      X Offset at which to paint image at
     * @param y_offset      Y Offset at which to paint image at
     * @param x_scale       X Scale Factor
     * @param y_scale       Y Scale Factor
     */
    
    public void paint(Graphics g, int x_offset, int y_offset, int x_scale, int y_scale)
    {
        g.drawImage(image,x_offset,y_offset,x_scale,y_scale,null);
    }
    
    
    /**
     * Paint the FastImage
     * 
     * @param g         Graphics context
     * @param x1            Destination top left corner
     * @param y1            Destination top left corner
     * @param x2            Destination bottom right corner
     * @param y2            Destination bottom right corner
     * @param x3            Source top left corner
     * @param y3            Source top left corner
     * @param x4            Source bottom right corner
     * @param y4            Source bottom right corner
     */
    
    public void paint(Graphics g, int x1, int y1, int x2, int y2, int x3, int y3, int x4, int y4)
    {
        g.drawImage(image, x1, y1, x2, y2, x3, y3, x4, y4, null);
    }


    /**
     *  Registers an ImageConsumer with the ImageProducer for access to the image data 
     *  during a later reconstruction of the Image.
     * 
     *  @param ic            the specified ImageConsumer
     */

    public void addConsumer(ImageConsumer ic) 
    {
        // Register Image Consumer
        consumer = ic;

        // Set Image Dimensions
        consumer.setDimensions(width,height);

        // Set Image Consumer Hints for speed
        consumer.setHints(ImageConsumer.TOPDOWNLEFTRIGHT|ImageConsumer.COMPLETESCANLINES|ImageConsumer.SINGLEPASS|ImageConsumer.SINGLEFRAME);

        // Set Image Colour Model
        consumer.setColorModel(model);
    }

    /**
     * Check if consumer is already registered
     * 
     * @return always returns true
     */

    public boolean isConsumer(ImageConsumer ic) 
    {
        return true;
    }

    /**
     *  Registers the specified ImageConsumer object as a consumer and starts an immediate 
     *  reconstruction of the image data which will then be delivered to this consumer and 
     *  any other consumer which might have already been registered with the producer.
     * 
     * @param ic        the specified ImageConsumer
     */

    public void startProduction(ImageConsumer ic) 
    {
        addConsumer(ic);
    }
    
    public void removeConsumer(ImageConsumer ic){}
    public void requestTopDownLeftRightResend(ImageConsumer ic){}
}