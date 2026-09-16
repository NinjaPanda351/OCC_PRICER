package com.cardpricer.service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;

/** Common bounded image loading for previews and high-value verification. */
public final class CardImageLoader {
    private CardImageLoader() {}
    public static BufferedImage read(String url) throws IOException {
        URI uri=URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IOException("Card images require HTTPS");
        HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();
        connection.setConnectTimeout(10_000);connection.setReadTimeout(10_000);
        try {
            byte[] bytes;
            try(var input=connection.getInputStream()) {
                bytes=input.readNBytes(8*1024*1024+1);
                if(bytes.length>8*1024*1024)throw new IOException("Image too large");
            }
            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Image request cancelled");
            try(var imageInput=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(imageInput);
                if(!readers.hasNext())throw new IOException("Unsupported image");
                var reader=readers.next();
                try {
                    reader.setInput(imageInput);
                    if((long)reader.getWidth(0)*reader.getHeight(0)>20_000_000L)throw new IOException("Image dimensions too large");
                    return reader.read(0);
                }finally{reader.dispose();}
            }
        }finally{connection.disconnect();}
    }
}
