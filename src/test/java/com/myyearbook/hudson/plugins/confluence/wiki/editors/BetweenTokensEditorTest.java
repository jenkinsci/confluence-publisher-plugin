package com.myyearbook.hudson.plugins.confluence.wiki.editors;

import hudson.model.TaskListener;
import junit.framework.TestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;
import org.mockito.Mock;

import static org.mockito.MockitoAnnotations.initMocks;

import com.myyearbook.hudson.plugins.confluence.wiki.editors.MarkupEditor.TokenNotFoundException;
import com.myyearbook.hudson.plugins.confluence.wiki.generators.MarkupGenerator;

public class BetweenTokensEditorTest extends TestCase {

    private static final String START_TOKEN = "%start%";
    private static final String END_TOKEN = "%end%";

    @Mock
    TaskListener buildListener;
    @Mock
    MarkupGenerator markupGenerator;

    @Before
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        initMocks(this);
    }

    @After
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Tests that an error occurs if the START marker is not found.
     *
     * @throws TokenNotFoundException
     */
    @Bug(14205)
    @Test(expected = TokenNotFoundException.class)
    public void testPerformEdits_startMarkerNotFound() throws TokenNotFoundException {
        String testContent = "The start marker is nowhere to be found.%end%";
        String toInsert = "New Content!";
        String expectedMessage = "Start-marker token could not be found in the page content:";

        BetweenTokensEditor obj = new BetweenTokensEditor(markupGenerator, START_TOKEN, END_TOKEN);

        try {
            obj.performEdits(buildListener, testContent, toInsert, false);
            fail("Expected TokenNotFoundException");
        } catch (TokenNotFoundException exc) {
            assertTrue(exc.getMessage().startsWith(expectedMessage));
        }
    }

    /**
     * Tests that an error occurs if the END marker is not found.
     *
     * @throws TokenNotFoundException
     */
    @Test(expected = TokenNotFoundException.class)
    public void testPerformEdits_endMarkerNotFound() throws TokenNotFoundException {
        String testContent = "%start%The end marker is nowhere to be found.";
        String toInsert = "New Content!";
        String expectedMessage = "End-marker token could not be found after the start-marker token:";

        BetweenTokensEditor obj = new BetweenTokensEditor(markupGenerator, START_TOKEN, END_TOKEN);

        try {
            obj.performEdits(buildListener, testContent, toInsert, false);
            fail("Expected TokenNotFoundException");
        } catch (TokenNotFoundException exc) {
            assertTrue(exc.getMessage().startsWith(expectedMessage));
        }
    }

    /**
     * This tests that you can replace two sections -- each with distinct START tokens, but sharing
     * the same END token (e.g., "<tt>&lt;/div&gt;</tt>").
     *
     * @throws TokenNotFoundException
     */
    @Bug(13896)
    public void testPerformEdits_multipleMarkers() throws TokenNotFoundException {
        String testContent = "%start1%First Section.%end%\n%start%Second Section.%end%";
        String toInsert = "First replacement";

        BetweenTokensEditor obj1 = new BetweenTokensEditor(markupGenerator, "%start1%", END_TOKEN);
        String actual = obj1.performEdits(buildListener, testContent, toInsert, true);
        assertEquals("%start1%First replacement%end%\n%start%Second Section.%end%", actual);

        toInsert = "Second replacement";
        BetweenTokensEditor obj2 = new BetweenTokensEditor(markupGenerator, START_TOKEN, END_TOKEN);
        actual = obj2.performEdits(buildListener, actual, toInsert, true);
        assertEquals("%start1%First replacement%end%\n%start%Second replacement%end%", actual);
    }

    /**
     * This tests that the old wiki format (Confluence pre-4.0) inserts newlines around the content.
     *
     * @throws TokenNotFoundException
     */
    public void testPerformEdits_oldFormat() throws TokenNotFoundException {
        String testContent = "Header\n%start%Current Content%end%\nFooter";
        String toInsert = "Replacement content";

        BetweenTokensEditor obj = new BetweenTokensEditor(markupGenerator, START_TOKEN, END_TOKEN);
        String actual = obj.performEdits(buildListener, testContent, toInsert, false);
        assertEquals("Header\n%start%\nReplacement content\n%end%\nFooter", actual);
    }

    @Test
    public void testCtor() {
        BetweenTokensEditor obj = new BetweenTokensEditor(markupGenerator, START_TOKEN, END_TOKEN);
        assertEquals(START_TOKEN, obj.startMarkerToken);
        assertEquals(END_TOKEN, obj.endMarkerToken);
    }

}
