package de.ruedigermoeller.serialization;

import de.ruedigermoeller.serialization.minbin.MBIn;
import de.ruedigermoeller.serialization.minbin.MinBin;
import de.ruedigermoeller.serialization.util.FSTUtil;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;

/**
 * Copyright (c) 2012, Ruediger Moeller. All rights reserved.
 * <p/>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 * <p/>
 * Date: 02.04.2014
 * Time: 19:13
 * To change this template use File | Settings | File Templates.
 */
public class FSTMixDecoder implements FSTDecoder {
    
    FSTConfiguration conf;
    MBIn input;
    private InputStream inputStream;

    public FSTMixDecoder(FSTConfiguration conf) {
        this.conf = conf;
        input = new MBIn(null,0);
    }

    @Override
    public String readStringUTF() throws IOException {
        Object read = input.readObject();
        if ( read instanceof String )
            return (String) read;
        // in case preceding atom has been consumed b[] => str 8 char[] => str 16;
        if ( read instanceof byte[] ) {
            return new String((byte[])read,0,0,((byte[]) read).length);
        } else if ( read instanceof char[] ) {
            return new String((char[])read,0,((char[]) read).length);
        } else if ( read == MinBin.END_MARKER )
            return null;
        throw new RuntimeException("Expected String, byte[], char[] or tupel end");
    }

    @Override
    public String readStringAsc() throws IOException {
        return (String) input.readObject();
    }

    @Override
    /**
     * if array is null => create own array. if len == -1 => use len read
     */
    public Object readFPrimitiveArray(Object array, Class componentType, int len) {
        if ( componentType == double.class ) {
            double[] da = (double[]) array;
            for (int i = 0; i < da.length; i++) {
                da[i] = (double) input.readTag(input.readIn());
            }
            return da;
        }
        if ( componentType == float.class ) {
            float[] da = (float[]) array;
            for (int i = 0; i < da.length; i++) {
                da[i] = (float) input.readTag(input.readIn());
            }
            return da;
        }
        Object arr = array; // input.readObject();
        int length = Array.getLength(arr);
        if ( len != -1 && len != length)
            throw new RuntimeException("unexpected arrays size");
        byte type = 0;
        if (componentType == boolean.class)    type |= MinBin.INT_8;
        else if (componentType == byte.class)  type |= MinBin.INT_8;
        else if (componentType == short.class) type |= MinBin.INT_16;
        else if (componentType == char.class)  type |= MinBin.INT_16 | MinBin.UNSIGN_MASK;
        else if (componentType == int.class)   type |= MinBin.INT_32;
        else if (componentType == long.class)  type |= MinBin.INT_64;
        else throw new RuntimeException("unsupported type " + componentType.getName());
        input.readArrayRaw(type,len,array);
        return arr;
    }

    @Override
    public void readFIntArr(int len, int[] arr) throws IOException {
        int res[] = (int[]) input.readObject();
        for (int i = 0; i < len; i++) {
              arr[i] = res[i];
        }
    }

    @Override
    public int readFInt() throws IOException {
        return (int) input.readInt();
    }

    @Override
    public double readFDouble() throws IOException {
        return (double) input.readObject();
    }

    @Override
    public float readFFloat() throws IOException {
        return (float) input.readObject();
    }

    @Override
    public byte readFByte() throws IOException {
        return (byte) input.readInt();
    }

    @Override
    public long readFLong() throws IOException {
        return input.readInt();
    }

    @Override
    public char readFChar() throws IOException {
        return (char) input.readInt();
    }

    @Override
    public short readFShort() throws IOException {
        return (short) input.readInt();
    }

    @Override
    public int readPlainInt() throws IOException {
        throw new RuntimeException("not supported");
    }

    @Override
    public byte[] getBuffer() {
        return input.getBuffer();
    }

    @Override
    public int getInputPos() {
        return input.getPos();
    }

    @Override
    public void moveTo(int position) {
        throw new RuntimeException("not supported");
    }

    @Override
    public void setInputStream(InputStream in) {
        this.inputStream = in;
        if ( in != null ) {
            try {
                int count = 0;
                int chunk_size = 1000;
                byte buf[] = input.getBuffer(); 
                if (buf==null) {
                    buf = new byte[chunk_size];
                }
                int read = in.read(buf);
                count+=read;
                while( read != -1 ) {
                    try {
                        if ( buf.length < count+chunk_size ) {
                            byte tmp[] = new byte[buf.length*2];
                            System.arraycopy(buf,0,tmp,0,count);
                            buf = tmp;
                        }
                        read = in.read(buf,count,chunk_size);
                        if ( read > 0 )
                            count += read;
                    } catch ( IndexOutOfBoundsException iex ) {
                        read = -1; // many stream impls break contract
                    }
                }
                in.close();
                input.setBuffer(buf, count);
            } catch (IOException e) {
                FSTUtil.rethrow(e);
            }            
        }
    }

    @Override
    public void ensureReadAhead(int bytes) {
    }

    @Override
    public void reset() {
        input.reset();
    }

    @Override
    public void resetToCopyOf(byte[] bytes, int off, int len) {
        if (off != 0 )
            throw new RuntimeException("not supported");
        input.setBuffer(bytes,len);
    }

    @Override
    public void resetWith(byte[] bytes, int len) {
        input.setBuffer(bytes,len);
    }

    public int getObjectHeaderLen() // len field of last header read (if avaiable)
    {
        if ( lastObjectLen < 0 )
            return (int) input.readInt();
        return lastObjectLen;
    }

    int lastObjectLen;
    Class lastDirectClass;
    public byte readObjectHeaderTag() throws IOException {
        lastObjectLen = -1;
        byte tag = input.peekIn();
        lastDirectClass = null;
        if ( MinBin.isTag(tag) ) {
            if ( MinBin.getTagId(tag) == MinBin.HANDLE ) {
                input.readIn(); // consume
                return FSTObjectOutput.HANDLE;
            }
            if ( MinBin.getTagId(tag) == MinBin.STRING )
                return FSTObjectOutput.STRING;
            if ( MinBin.getTagId(tag) == MinBin.BOOL ) {
                Boolean b = (Boolean) input.readObject();
                return b ? FSTObjectOutput.BIG_BOOLEAN_TRUE : FSTObjectOutput.BIG_BOOLEAN_FALSE;
            }
            if (    MinBin.getTagId(tag) == MinBin.DOUBLE ||
                    MinBin.getTagId(tag) == MinBin.DOUBLE_ARR ||
                    MinBin.getTagId(tag) == MinBin.FLOAT_ARR ||
                    MinBin.getTagId(tag) == MinBin.FLOAT
            )
            {
                lastReadDirectObject = input.readObject();
                return FSTObjectOutput.DIRECT_OBJECT;
            }
            input.readIn();
            if (MinBin.getTagId(tag) == MinBin.SEQUENCE) {
                try {
                    lastDirectClass = conf.getClassRegistry().classForName(conf.getClassForCPName((String) input.readObject()));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                if ( lastDirectClass.isArray() )
                    return FSTObjectOutput.ARRAY;
                else {
                    input.readInt(); // consume -1 for unknown sequence length
                    return FSTObjectOutput.OBJECT; // with serializer
                }
            }
            return FSTObjectOutput.OBJECT;
        }
        lastReadDirectObject = input.readObject();
        return FSTObjectOutput.DIRECT_OBJECT;
    }
    
    public Object getDirectObject() // in case class already resolves to read object (e.g. mix input)
    {
        Object tmp = lastReadDirectObject;
        lastReadDirectObject = null;
        return tmp;
    }

    Object lastReadDirectObject; // in case readClass already reads full minbin value
    @Override
    public FSTClazzInfo readClass() throws IOException, ClassNotFoundException {
        if (lastDirectClass != null ) {
            FSTClazzInfo clInfo = conf.getCLInfoRegistry().getCLInfo(lastDirectClass);
            lastDirectClass = null;
            return clInfo;
        }
        Object read = input.readObject();
        String name = (String) read;
        String clzName = conf.getClassForCPName(name);
        return conf.getCLInfoRegistry().getCLInfo(classForName(clzName));
    }

    @Override
    public Class classForName(String name) throws ClassNotFoundException {
        return Class.forName(name);
    }

    @Override
    public void registerClass(Class possible) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void close() {
        //TODO
        throw new RuntimeException("not implemented");
    }

    @Override
    public void skip(int n) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void readPlainBytes(byte[] b, int off, int len) {
        for (int i = 0; i < len; i++) {
            b[i+off] = input.readIn();
        }
    }

    @Override
    public boolean isMapBased() {
        return true;
    }

    public void consumeEndMarker() {
        byte type = input.peekIn();
        if (type==MinBin.END) {
            input.readIn();
        }
    }

    @Override
    public Class readArrayHeader() throws IOException, ClassNotFoundException, Exception {
        byte tag = input.peekIn(); // need to be able to consume MinBin Sequence tag silently
        if ( MinBin.getTagId(tag) == MinBin.NULL ) {
            input.readIn();
            return null;
        }
        if ( lastDirectClass != null )
            return readClass().getClazz();
        if ( MinBin.getTagId(tag) == MinBin.SEQUENCE ) {
            input.readIn(); // consume (multidim array)
        } else if ( MinBin.isPrimitive(tag) ) {
            input.readIn(); // consume tag
            switch (MinBin.getBaseType(tag)) {
                case MinBin.INT_8:
                    return byte[].class;
                case MinBin.INT_16:
                    if (MinBin.isSigned(tag) )
                        return short[].class;
                    return char[].class;
                case MinBin.INT_32:
                    return int[].class;
                case MinBin.INT_64:
                    return long[].class;
            }
        }
        return readClass().getClazz();
    }

}