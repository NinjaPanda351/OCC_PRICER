package com.cardpricer.service;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ProviderCardStreamTest {
    @Test void prettyArraysAndJsonLinesHaveEquivalentRecords() throws Exception {
        var array=new ArrayList<String>();var lines=new ArrayList<String>();
        ProviderCardStream.read(new StringReader("[\n{\n\"name\":\"One\"\n},\n{\"name\":\"Two\"}\n]"),()->false,j->array.add(j.getString("name")));
        ProviderCardStream.read(new StringReader("{\"name\":\"One\"}\n{\"name\":\"Two\"}\n"),()->false,j->lines.add(j.getString("name")));
        assertEquals(java.util.List.of("One","Two"),array);assertEquals(array,lines);
    }
    @Test void truncatedAndMalformedDocumentsAreRejected() {
        for(String invalid:java.util.List.of("[{\"name\":\"One\"}","[{\"name\":\"One\"},]","[{}]junk","{}\nnot json","[{\"name\":"))
            assertThrows(java.io.IOException.class,()->ProviderCardStream.read(new StringReader(invalid),()->false,j->{}),invalid);
    }
    @Test void cancellationStopsBetweenRecords() {
        var names=new ArrayList<String>();
        assertThrows(InterruptedException.class,()->ProviderCardStream.read(new StringReader("{}\n{}"),()->names.size()==1,j->names.add("one")));
        assertEquals(1,names.size());
    }
}
