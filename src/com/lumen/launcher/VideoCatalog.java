package com.lumen.launcher;

/**
 * Catalogue fermé des fonds vidéo : des fichiers précis de Wikimedia Commons, choisis un par un
 * d'après leur description (vraies prises de vue : drone, timelapse, caméra 4K de la Station spatiale).
 * Ni recherche par mots-clés, ni conférences, ni simulations ou visualisations de données.
 * Chaque groupe : nom affiché, puis titres exacts des pages « File: », dans l'ordre de préférence.
 * La licence libre est revérifiée au téléchargement (NetPolicy.freeLicense). Java pur, testé.
 */
final class VideoCatalog {
    private VideoCatalog() { }

    static final String NATURE = "Nature", SPACE = "Espace et Terre";

    /** Villes (choisies dans Réglages › Villes). */
    static final String[][] CITIES = {
            {"Paris",
                    "File:Paris - 4K Hyperlapse - YouTube.webm",
                    "File:Eiffel Tower Drone 4k-Qx c1X3zfEc-313-251.webm",
                    "File:Arc de Triomphe, Paris 2022.webm",
                    "File:The River Seine & Louvre, Paris 2022.webm",
                    "File:Le Jardin du Luxembourg, Paris 2022.webm",
                    "File:Eiffel Tower, Paris 2022.webm"},
            {"New York",
                    "File:Flight over mid Manhattan New York.webm",
                    "File:Aerial View of The Vessel at Hudson Yards, New York City.webm",
                    "File:Aerial views of Hells Kitchen and East Midtown Manhattan in New York City, USA at night.webm",
                    "File:Statue Of Liberty 4k Drone.webm",
                    "File:Aerial views of Downtown Manhattan, New York City, USA at night including Battery Park, Freedom Tower, Wall Street, The Oculus, West Side Highway and Hudson River.webm",
                    "File:Aerial views of Hells Kitchen, West Midtown Manhattan, Mercedes House, Hudson Yards, West Side Highway, Billionaires Row, New York City, USA.webm"},
            {"Hong Kong",
                    "File:Hong Kong Island Night Cityscape 2020.webm",
                    "File:Stanley Hong Kong Drone 2020.webm"},
            {"Chicago",
                    "File:EVERY ANGLE OF DOWNTOWN CHICAGO CAPTURED BY DRONE!.webm"},
            {"San Francisco",
                    "File:Golden Gate Bridge.webm",
                    "File:Golden Gate Bridge Timelapse no audio.webm"},
            {"Stockholm",
                    "File:Stockholm epic morning drone flight.webm"}};

    /** Paysages filmés (drone, timelapse). */
    static final String[] NATURE_FILES = {
            "File:MADEIRA - Welcome to Paradise - 4K Drone Video.webm",
            "File:Mistail, aerial video.webm",
            "File:The Pyrenees by Drone - Breche de Roland - Avalanche-Cirque de Gavarnie - Ordesa in 4K.webm",
            "File:Drone Footage Black Balsam Knob NC 4k.webm",
            "File:Piz Neir, aerial video 4k.webm",
            "File:After Storm (4K Resolution).webm",
            "File:4K Nature Cinematography recorded with Nikon D5300.webm",
            "File:Piz Linard, aerial video.webm",
            "File:Grass with dew time-lapse.webm",
            "File:Survol du Massif de Coutach.webm",
            "File:Wasserfluss bei Söll in Tirol 20220904 C0471.webm"};

    /** La Terre filmée depuis la Station spatiale, et le ciel étoilé : prises de vue réelles uniquement. */
    static final String[] SPACE_FILES = {
            "File:Earth Day 2017 - 4K Earth Views From Space.webm",
            "File:ISS 4K Crew Earth Observations.webm",
            "File:Aurora Borealis from Space (4K).webm",
            "File:View of Planet Earth (4K).webm",
            "File:'Home' - 4K Views from Space.webm",
            "File:Crew Earth Observations (4K).webm",
            "File:Ocean Moon Glint and City Night Lights in 4K.webm",
            "File:JAPAN Milk Way 4K - Beautiful Star and Sky at Night Time Lapse.webm",
            "File:Following the Milky Way over ALMA.webm"};

    /** Groupe d'un titre de page (ville, NATURE ou SPACE), ou null s'il n'est pas au catalogue. */
    static String groupOf(String pageTitle) {
        if (pageTitle == null) return null;
        String t = pageTitle.replace('_', ' ').trim();
        for (String[] c : CITIES) for (int i = 1; i < c.length; i++) if (c[i].equals(t)) return c[0];
        for (String f : NATURE_FILES) if (f.equals(t)) return NATURE;
        for (String f : SPACE_FILES) if (f.equals(t)) return SPACE;
        return null;
    }

    /** Titres d'un groupe, dans l'ordre de préférence. */
    static String[] filesOf(String group) {
        if (NATURE.equals(group)) return NATURE_FILES;
        if (SPACE.equals(group)) return SPACE_FILES;
        for (String[] c : CITIES) if (c[0].equals(group)) return java.util.Arrays.copyOfRange(c, 1, c.length);
        return new String[0];
    }

    static boolean isCity(String name) {
        for (String[] c : CITIES) if (c[0].equals(name)) return true;
        return false;
    }

    /** Tous les titres séparés par « | » (paramètre titles= de l'API, 50 au maximum). */
    static String allTitles() {
        StringBuilder b = new StringBuilder();
        for (String[] c : CITIES) for (int i = 1; i < c.length; i++) add(b, c[i]);
        for (String f : NATURE_FILES) add(b, f);
        for (String f : SPACE_FILES) add(b, f);
        return b.toString();
    }

    static int count() { return allTitles().split("\\|").length; }

    private static void add(StringBuilder b, String f) { if (b.length() > 0) b.append('|'); b.append(f); }
}
