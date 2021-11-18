package com.myyearbook.hudson.plugins.confluence.wiki.editors;

import hudson.model.TaskListener;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mock;

import com.myyearbook.hudson.plugins.confluence.wiki.editors.MarkupEditor.TokenNotFoundException;
import com.myyearbook.hudson.plugins.confluence.wiki.generators.MarkupGenerator;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BetweenTokensEditorTest {

    private static final String START_TOKEN = "%start%";
    private static final String END_TOKEN = "%end%";

    private AutoCloseable closeable;

    @Mock
    TaskListener buildListener;
    @Mock
    MarkupGenerator markupGenerator;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
    }

    @After
    public void tearDown() throws Exception {
        closeable.close();
    }

    /**
     * Tests that an error occurs if the START marker is not found.
     *
     * @throws TokenNotFoundException
     */
    @Issue("JENKINS-14205")
    @Test
    public void testPerformEdits_startMarkerNotFound() {
        String testContent = "The start marker is nowhere to be found.%end%";
        String toInsert = "New Content!";
        String expectedMessage = "Start-marker token could not be found in the page content:";

        BetweenTokensEditor obj = new BetweenTokensEditor(markupGenerator, START_TOKEN, END_TOKEN);

        TokenNotFoundException exc = assertThrows(TokenNotFoundException.class, () -> obj.performEdits(buildListener, testContent, toInsert, false));
        assertTrue(exc.getMessage().startsWith(expectedMessage));
    }

    /**
     * Tests that an error occurs if the END marker is not found.
     *
     * @throws TokenNotFoundException
     */
    @Test
    public void testPerformEdits_endMarkerNotFound() {
        String testContent = "%start%The end marker is nowhere to be found.";
        String toInsert = "New Content!";
        String expectedMessage = "End-marker token could not be found after the start-marker token:";

        BetweenTokensEditor obj = new BetweenTokensEditor(markupGenerator, START_TOKEN, END_TOKEN);

        TokenNotFoundException exc = assertThrows(TokenNotFoundException.class, () -> obj.performEdits(buildListener, testContent, toInsert, false));
        assertTrue(exc.getMessage().startsWith(expectedMessage));
    }

    /**
     * This tests that you can replace two sections -- each with distinct START tokens, but sharing
     * the same END token (e.g., "<tt>&lt;/div&gt;</tt>").
     *
     * @throws TokenNotFoundException
     */
    @Issue("JENKINS-13896")
    @Test
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
    @Test
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
