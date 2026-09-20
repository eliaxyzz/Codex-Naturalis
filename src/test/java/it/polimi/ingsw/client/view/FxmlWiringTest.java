package it.polimi.ingsw.client.view;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks each FXML against its controller without starting JavaFX: an @FXML field with no
 * matching fx:id is null at runtime, and an onAction naming a method that isn't there
 * fails the moment the view is opened.
 */
class FxmlWiringTest {
    private static final Path VIEWS = Path.of("src/main/resources/it/polimi/ingsw/client/view");

    @TestFactory
    Stream<DynamicTest> everyFxmlMatchesItsController() throws Exception {
        try (var files = Files.list(VIEWS)) {
            List<Path> fxmls = files.filter(p -> p.toString().endsWith(".fxml")).sorted().toList();
            assertFalse(fxmls.isEmpty(), "no FXML files found, the path must be wrong");
            return fxmls.stream().map(fxml -> DynamicTest.dynamicTest(fxml.getFileName().toString(),
                    () -> checkWiring(fxml)));
        }
    }

    private void checkWiring(Path fxml) throws Exception {
        Document document;
        try (InputStream in = Files.newInputStream(fxml)) {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        }
        Element root = document.getDocumentElement();
        String controllerName = root.getAttribute("fx:controller");
        if (controllerName.isEmpty()) return;
        Class<?> controller = Class.forName(controllerName);

        Set<String> ids = new HashSet<>();
        List<String> actions = new ArrayList<>();
        collect(root, ids, actions);

        for (Field field : allFields(controller)) {
            if (field.getAnnotation(javafx.fxml.FXML.class) == null) continue;
            assertTrue(ids.contains(field.getName()),
                    fxml.getFileName() + " has no fx:id for @FXML field '" + field.getName()
                            + "' of " + controller.getSimpleName() + ": it would be null at runtime");
        }
        for (String action : actions) {
            assertTrue(hasMethod(controller, action),
                    fxml.getFileName() + " points onAction at '" + action + "', which "
                            + controller.getSimpleName() + " does not declare");
        }
    }

    private void collect(Element element, Set<String> ids, List<String> actions) {
        String id = element.getAttribute("fx:id");
        if (!id.isEmpty()) ids.add(id);
        for (String attribute : new String[]{"onAction", "onMouseClicked"}) {
            String value = element.getAttribute(attribute);
            if (value.startsWith("#")) actions.add(value.substring(1));
        }
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) collect(child, ids, actions);
        }
    }

    private List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            fields.addAll(List.of(c.getDeclaredFields()));
        }
        return fields;
    }

    private boolean hasMethod(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name)) return true;
            }
        }
        return false;
    }
}
