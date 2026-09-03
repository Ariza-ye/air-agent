package com.ariza.agent.tool.gdal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GdalCommandParserTest {

    @Test
    void parsesSimpleCommand() {
        GdalCommand command = GdalCommandParser.parse("gdalinfo /data/sample.tif");
        assertNotNull(command);
        assertEquals("gdalinfo", command.tool());
        assertEquals(List.of("/data/sample.tif"), command.args());
    }

    @Test
    void parsesCommandWithMultipleArguments() {
        GdalCommand command = GdalCommandParser.parse("ogrinfo -so -al /data/layer.shp");
        assertNotNull(command);
        assertEquals("ogrinfo", command.tool());
        assertEquals(List.of("-so", "-al", "/data/layer.shp"), command.args());
    }

    @Test
    void parsesDoubleQuotedArgument() {
        GdalCommand command = GdalCommandParser.parse("gdal_calc.py --calc=\"A*(B>0)\"");
        assertNotNull(command);
        assertEquals("gdal_calc.py", command.tool());
        assertEquals(List.of("--calc=A*(B>0)"), command.args());
    }

    @Test
    void parsesSingleQuotedArgumentWithSpaces() {
        GdalCommand command = GdalCommandParser.parse("ogrinfo '/data/my layer.shp'");
        assertNotNull(command);
        assertEquals(List.of("/data/my layer.shp"), command.args());
    }

    @Test
    void parsesBackslashEscapedSpace() {
        GdalCommand command = GdalCommandParser.parse("gdalinfo /data/a\\ b.tif");
        assertNotNull(command);
        assertEquals(List.of("/data/a b.tif"), command.args());
    }

    @Test
    void parsesNestedQuotesMixedWithPlainArguments() {
        GdalCommand command = GdalCommandParser.parse("gdal_translate -of JPEG \"/data/out.jpg\" /data/in.tif");
        assertNotNull(command);
        assertEquals("gdal_translate", command.tool());
        assertEquals(List.of("-of", "JPEG", "/data/out.jpg", "/data/in.tif"), command.args());
    }

    @Test
    void rejectsNullCommand() {
        assertNull(GdalCommandParser.parse(null));
    }

    @Test
    void rejectsBlankCommand() {
        assertNull(GdalCommandParser.parse("   "));
    }

    @Test
    void rejectsCommandOutsideWhitelist() {
        assertNull(GdalCommandParser.parse("rm -rf /data"));
        assertNull(GdalCommandParser.parse("ls /data"));
        assertNull(GdalCommandParser.parse("python3 /data/evil.py"));
    }

    @Test
    void rejectsShellMetacharacterInArguments() {
        assertNull(GdalCommandParser.parse("gdalinfo /data/x;rm -rf /"));
        assertNull(GdalCommandParser.parse("gdalinfo /data/x | wc -l"));
        assertNull(GdalCommandParser.parse("gdalinfo $(ls)"));
        assertNull(GdalCommandParser.parse("gdalinfo /data/x &"));
        assertNull(GdalCommandParser.parse("gdalinfo /data/x && gdalinfo /data/y"));
        assertNull(GdalCommandParser.parse("gdalinfo /data/x\nrm -rf /data"));
    }

    @Test
    void rejectsUnclosedQuote() {
        assertNull(GdalCommandParser.parse("gdalinfo '/data/x"));
    }

    @Test
    void allowsLiteralOperatorCharactersForCalcExpressions() {
        GdalCommand command = GdalCommandParser.parse("gdal_calc.py --calc=A*(B>0)");
        assertNotNull(command);
        assertEquals(List.of("--calc=A*(B>0)"), command.args());
    }

    @Test
    void allowsQuotedMetacharacters() {
        GdalCommand command = GdalCommandParser.parse("gdal_calc.py --calc='A>0'");
        assertNotNull(command);
        assertEquals(List.of("--calc=A>0"), command.args());
    }

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        GdalCommand command = GdalCommandParser.parse("  gdalinfo /data/sample.tif  ");
        assertNotNull(command);
        assertEquals("gdalinfo", command.tool());
    }
}