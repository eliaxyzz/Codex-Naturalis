package it.polimi.ingsw.server.model.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import it.polimi.ingsw.server.model.card.*;
import it.polimi.ingsw.server.model.card.GoldCardStrategy.*;
import it.polimi.ingsw.util.customexceptions.CannotOpenJSONException;
import it.polimi.ingsw.util.customexceptions.InvalidIdException;
import it.polimi.ingsw.util.supportclasses.Resource;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * This class handles reading card data from JSON files. It provides methods to extract specific information
 * about card properties based on the JSON structure.
 */
public class JsonCardsReader {
    public static final String RESOURCE_CARDS = "JsonResourceCards.json";
    public static final String GOLD_CARDS = "JsonGoldCards.json";
    public static final String STARTER_CARDS = "JsonStarterCards.json";

    //every new game builds ~90 cards, so each file is parsed once and kept by card id.
    //The parsed objects are only ever read after this point.
    private static final Map<String, Map<Integer, JSONObject>> cardsByFile = new ConcurrentHashMap<>();

    /**
     * @param file One of RESOURCE_CARDS, GOLD_CARDS, STARTER_CARDS.
     * @return The ids of every card described in that file, in ascending order.
     * @throws IllegalStateException If the file can't be read: the game can't run without its cards.
     */
    public static List<Integer> cardIds(String file) {
        try {
            return cardsIn(file).keySet().stream().sorted().toList();
        } catch (CannotOpenJSONException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private static JSONObject cardData(String file, int id) throws CannotOpenJSONException, InvalidIdException {
        JSONObject item = cardsIn(file).get(id);
        if (item == null) throw new InvalidIdException("invalid id: " + id);
        return item;
    }

    private static Map<Integer, JSONObject> cardsIn(String file) throws CannotOpenJSONException {
        Map<Integer, JSONObject> cards = cardsByFile.get(file);
        if (cards == null) {
            cards = parse(file);
            cardsByFile.putIfAbsent(file, cards);
        }
        return cards;
    }

    private static Map<Integer, JSONObject> parse(String file) throws CannotOpenJSONException {
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(file);
        if (is == null) throw new CannotOpenJSONException("missing resource " + file);
        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JSONArray dataArray = (JSONArray) ((JSONObject) new JSONParser().parse(reader)).get("data");
            Map<Integer, JSONObject> cards = new HashMap<>();
            for (Object obj : dataArray) {
                JSONObject item = (JSONObject) obj;
                cards.put(((Long) item.get("Id")).intValue(), item);
            }
            return Map.copyOf(cards);
        } catch (IOException | ParseException e) {
            throw new CannotOpenJSONException("couldn't load " + file + ": " + e.getMessage());
        }
    }

    /**
     * Extracts the resource type from a JSONObject representing a corner of a card in the JSON data.
     * @param item JSONObject representing the card data.
     * @param corn String representing the corner.
     * @return `Resource` enum value extracted from the corner data.
     */
    private static Resource getCornerResource(JSONObject item, String corn) {
        JSONObject corner = (JSONObject) item.get(corn);
        String resource = corner.get("Resource").toString();
        return Resource.StringToResource(resource);
    }

    /**
     * Extracts the attachable flag from a JSONObject representing a corner of a card in the JSON data.
     * @param item JSONObject representing the card data.
     * @param corn String representing the corner.
     * @return True if the "Exist" key in the corner data is true, false otherwise.
     */
    private static boolean getCornerAttachable(JSONObject item, String corn) {
        JSONObject corner = (JSONObject) item.get(corn);
        return (Boolean) corner.get("Exist");
    }

    /**
     * Loads resource card from json file.
     * @param id Unique id that identifies the card.
     * @param resourceCard  Reference to the card itself.
     * @throws CannotOpenJSONException If the JSON file cannot be opened or parsed.
     * @throws InvalidIdException If there's no resource card with that id.
     */
    public static void loadResourceCard(int id, ResourceCard resourceCard) throws CannotOpenJSONException, InvalidIdException {
        loadGenericPlaceableCardInformation(resourceCard, cardData(RESOURCE_CARDS, id), id);
    }

    /**
     * Loads gold card from json file.
     * @param id Unique id that identifies the card.
     * @param goldCard  Reference to the card itself.
     * @throws CannotOpenJSONException If the JSON file cannot be opened or parsed.
     * @throws InvalidIdException If there's no gold card with that id.
     */
    public static void loadGoldCard(int id, GoldCard goldCard) throws CannotOpenJSONException, InvalidIdException {
        JSONObject item = cardData(GOLD_CARDS, id);
        loadGenericPlaceableCardInformation(goldCard, item, id);
        loadGoldCardStrategy(goldCard, item);
        loadGoldCardRequirements(goldCard, (JSONArray) item.get("Requirements"));
    }

    /**
     * Loads generic placeable card information.
     * @param placeableCard Placeable card to load.
     * @param item JSONObject representing the card data.
     * @param id Unique identifier of the card.
     */
    private static void loadGenericPlaceableCardInformation(PlaceableCard placeableCard, JSONObject item, int id) {
        placeableCard.setId(id);
        placeableCard.setPoints(((Long) item.get("Points")).intValue());
        placeableCard.setCardKingdom(Resource.StringToResource(item.get("Kingdom").toString()));
        placeableCard.setFrontTopLeftCorner(new Corner(getCornerResource(item, "TopLeftCorner"), getCornerAttachable(item, "TopLeftCorner")));
        placeableCard.setFrontTopRightCorner(new Corner(getCornerResource(item, "TopRightCorner"), getCornerAttachable(item, "TopRightCorner")));
        placeableCard.setFrontBottomLeftCorner(new Corner(getCornerResource(item, "BottomLeftCorner"), getCornerAttachable(item, "BottomLeftCorner")));
        placeableCard.setFrontBottomRightCorner(new Corner(getCornerResource(item, "BottomRightCorner"), getCornerAttachable(item, "BottomRightCorner")));
        placeableCard.setBackTopLeftCorner(new Corner(Resource.none, true));
        placeableCard.setBackBottomLeftCorner(new Corner(Resource.none, true));
        placeableCard.setBackTopRightCorner(new Corner(Resource.none, true));
        placeableCard.setBackBottomRightCorner(new Corner(Resource.none, true));
        placeableCard.setFacingUp(true);
    }

    /**
     * Loads the strategy information for a gold card from a JSONObject.
     * @param goldCard Gold card to load.
     * @param item JSONObject representing the card data.
     */
    private static void loadGoldCardStrategy(GoldCard goldCard, JSONObject item)  {
        String strategy = item.get("Strategy").toString();
        switch (strategy) {
            case "coveredcorner" -> goldCard.setStrategy(new GoldCardCoveredCornerStrategy());
            case "noaction" -> goldCard.setStrategy(new GoldCardNoActionStrategy());
            case "feather" -> goldCard.setStrategy(new GoldCardFeatherStrategy());
            case "scroll" -> goldCard.setStrategy(new GoldCardScrollStrategy());
            case "inkpot" -> goldCard.setStrategy(new GoldCardInkPotStrategy());
            default -> throw new IllegalStateException("unknown gold card strategy: " + strategy);
        }
    }

    /**
     * Loads the resource requirements for a gold card from a JSONArray in the JSON data.
     * @param goldCard Gold card to load.
     * @param requirements JSONArray containing the resource requirements data.
     */
    private static void loadGoldCardRequirements(GoldCard goldCard, JSONArray requirements) {
        for (int i = 0; i < 4; i++) {
            JSONObject currentRequirement = (JSONObject) requirements.get(i);
            String resource = currentRequirement.get("Resource").toString();
            switch (Resource.StringToResource(resource)) {
                case fungi -> goldCard.setRequiredFungiResourceAmount(((Long) currentRequirement.get("Num")).intValue());
                case animal -> goldCard.setRequiredAnimalResourceAmount(((Long) currentRequirement.get("Num")).intValue());
                case plant -> goldCard.setRequiredPlantResourceAmount(((Long) currentRequirement.get("Num")).intValue());
                case insect -> goldCard.setRequiredInsectResourceAmount(((Long) currentRequirement.get("Num")).intValue());
                case none -> {}
            }
        }
    }

    /**
     * Loads starter card information from a JSON file based on the provided card ID.
     * @param id Unique id that identifies the card.
     * @param starterCard  Reference to the card itself.
     * @throws CannotOpenJSONException If the JSON file cannot be opened or parsed.
     * @throws InvalidIdException If there's no starter card with that id.
     */
    public static void loadStarterCard(int id, StarterCard starterCard) throws CannotOpenJSONException, InvalidIdException {
        loadStarterCardResourcesAndCorners(starterCard, cardData(STARTER_CARDS, id), id);
    }

    /**
     * Loads resource and corner information for a starter card from a JSONObject.
     * @param starterCard Starter card.
     * @param item JSONObject representing the card data.
     * @param id Unique identifier of the card.
     */
    private static void loadStarterCardResourcesAndCorners(StarterCard starterCard, JSONObject item,int id){
        List<Resource> backResources = new ArrayList<>();
        JSONArray resource = (JSONArray) item.get("ResourceBack");
        for(int i=0; i<3; i++) {
            backResources.add(Resource.StringToResource(resource.get(i).toString()));
        }
        starterCard.setId(id);
        starterCard.setFacingUp(true);
        starterCard.setBackCentralResources(new ArrayList<>(backResources));
        starterCard.setFrontTopLeftCorner(new Corner(getCornerResource(item, "FrontTopLeftCorner"), getCornerAttachable(item, "FrontTopLeftCorner")));
        starterCard.setFrontTopRightCorner(new Corner(getCornerResource(item, "FrontTopRightCorner"), getCornerAttachable(item, "FrontTopRightCorner")));
        starterCard.setFrontBottomLeftCorner(new Corner(getCornerResource(item, "FrontBottomLeftCorner"), getCornerAttachable(item, "FrontBottomLeftCorner")));
        starterCard.setFrontBottomRightCorner(new Corner(getCornerResource(item, "FrontBottomRightCorner"), getCornerAttachable(item, "FrontBottomRightCorner")));
        starterCard.setBackTopLeftCorner(new Corner(getCornerResource(item, "BackTopLeftCorner"), getCornerAttachable(item, "BackTopLeftCorner")));
        starterCard.setBackTopRightCorner(new Corner(getCornerResource(item, "BackTopRightCorner"), getCornerAttachable(item, "BackTopRightCorner")));
        starterCard.setBackBottomLeftCorner(new Corner(getCornerResource(item, "BackBottomLeftCorner"), getCornerAttachable(item, "BackBottomLeftCorner")));
        starterCard.setBackBottomRightCorner(new Corner(getCornerResource(item, "BackBottomRightCorner"), getCornerAttachable(item, "BackBottomRightCorner")));
    }
}
