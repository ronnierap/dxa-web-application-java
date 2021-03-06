package com.sdl.webapp.common.api.model.entity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.sdl.webapp.common.api.MediaHelper;
import com.sdl.webapp.common.exceptions.DxaException;
import com.sdl.webapp.common.util.ApplicationContextHolder;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
public class EclItemTest {

    @Autowired
    private ObjectMapper objectMapper;

    private static String removeXmlFromXpmString(String expectedXpmMarkup) {
        return expectedXpmMarkup.replaceFirst("<!-- Start Component Presentation: ", "").replaceFirst("-->", "");
    }

    @Test
    public void shouldPutComponentIdToMetadataIfEclUriIsSet() throws IOException {
        //given
        String uri = "uri",
                expectedXpmMarkup = "<!-- Start Component Presentation: { \"ComponentID\" : \"uri\" } -->";

        EclItem eclItem = new EclItem() {
        };
        eclItem.setUri(uri);
        eclItem.setXpmMetadata(new HashMap<String, Object>());

        //when
        String resultXpmMarkup = eclItem.getXpmMarkup(null);

        //then
        assertEquals(uri, eclItem.getXpmMetadata().get(EclItem.COMPONENT_ID_KEY));
        assertTrue(resultXpmMarkup.startsWith("<!-- Start Component Presentation: "));
        assertTrue(resultXpmMarkup.endsWith("-->"));
        assertEquals(readJsonToMap(removeXmlFromXpmString(expectedXpmMarkup)),
                readJsonToMap(removeXmlFromXpmString(resultXpmMarkup)));
    }

    private Map<String, String> readJsonToMap(String str) throws IOException {
        return objectMapper.readValue(str, new TypeReference<HashMap<String, String>>() {
        });
    }

    @Test
    public void shouldReturnEmptyStringIfXpmMetadataIsNull() {
        //given
        EclItem eclItem = new EclItem() {
        };

        //when
        String xpmMarkup = eclItem.getXpmMarkup(null);

        //then
        assertTrue(StringUtils.isEmpty(xpmMarkup));
    }

    @Test
    public void shouldReturnTemplateFragmentWhenToHtmlIsCalled() throws DxaException {
        //given
        String templateFragment = "templateFragment";
        EclItem eclItem = new EclItem() {
        };
        eclItem.setTemplateFragment(templateFragment);

        //when
        String toHtml = eclItem.toHtmlElement().toHtml();
        String toHtml1 = eclItem.toHtmlElement("100%").toHtml();
        String toHtml2 = eclItem.toHtmlElement("100%", 0.0, "", 0).toHtml();

        //then
        assertNotNull(toHtml);
        assertEquals(templateFragment, toHtml);
        assertEquals(toHtml, toHtml1);
        assertEquals(toHtml1, toHtml2);
    }

    @Test
    public void shouldReadSpecificEclDataFromXHtmlNode() {
        //given
        Node xhtmlNode = mock(Node.class);
        EclItem eclItem = new EclItem() {
        };
        NamedNodeMap map = mock(NamedNodeMap.class);

        when(xhtmlNode.getAttributes()).thenReturn(map);
        when(map.getNamedItem(anyString())).thenAnswer(new Answer<Node>() {
            @Override
            public Node answer(InvocationOnMock invocation) throws Throwable {
                Node node = mock(Node.class);
                String result = Objects.toString(invocation.getArguments()[0]);
                switch (result) {
                    case "xlink:href":
                        result = "0-1";
                        break;
                    case "data-multimediaFileSize":
                        result = "1024";
                        break;
                    default:
                        break;
                }

                when(node.getNodeValue()).thenReturn(result);
                return node;
            }
        });

        //when
        eclItem.readFromXhtmlElement(xhtmlNode);

        //then
        assertEquals("data-eclId", eclItem.getUri());
        assertEquals("data-eclDisplayTypeId", eclItem.getDisplayTypeId());
        assertEquals("data-eclTemplateFragment", eclItem.getTemplateFragment());
        assertEquals("data-eclFileName", eclItem.getFileName());
        assertEquals("data-eclMimeType", eclItem.getMimeType());
    }

    @org.springframework.context.annotation.Configuration
    static class SpringContext {
        @Bean
        public ApplicationContextHolder applicationContextHolder() {
            return new ApplicationContextHolder();
        }

        @SuppressWarnings("Duplicates")
        @Bean
        public ObjectMapper objectMapper() {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(SerializationFeature.INDENT_OUTPUT, false);
            objectMapper.registerModule(new JodaModule());
            objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
            return objectMapper;
        }

        @Bean
        public MediaHelper.MediaHelperFactory mediaHelperFactory() {
            return mock(MediaHelper.MediaHelperFactory.class);
        }
    }
}