/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper class for Cobblemon integration.
 * Uses reflection to provide soft dependency - Baritone works with or without Cobblemon installed.
 *
 * @author Baritone Contributors
 */
public final class CobblemonHelper {

    private static final String POKEMON_ENTITY_CLASS = "com.cobblemon.mod.common.entity.pokemon.PokemonEntity";
    private static final String COBBLEMON_SPECIES_CLASS = "com.cobblemon.mod.common.api.pokemon.PokemonSpecies";

    private static Boolean cobblemonPresent = null;
    private static Class<?> pokemonEntityClass = null;
    private static Method getPokemonMethod = null;
    private static Method getShinyMethod = null;
    private static Method getSpeciesMethod = null;
    private static Method getSpeciesNameMethod = null;
    private static Set<String> speciesNamesCache = null;

    private CobblemonHelper() {
        // Utility class
    }

    /**
     * Check if Cobblemon mod is installed and loaded.
     *
     * @return true if Cobblemon is present
     */
    public static boolean isCobblemonPresent() {
        if (cobblemonPresent == null) {
            try {
                pokemonEntityClass = Class.forName(POKEMON_ENTITY_CLASS);
                cobblemonPresent = true;
                initReflection();
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                cobblemonPresent = false;
            }
        }
        return cobblemonPresent;
    }

    /**
     * Initialize reflection methods for accessing Pokemon data.
     */
    private static void initReflection() {
        try {
            // Get the getPokemon() method from PokemonEntity
            getPokemonMethod = pokemonEntityClass.getMethod("getPokemon");
            
            // Get the Pokemon class and its methods
            Class<?> pokemonClass = getPokemonMethod.getReturnType();
            
            // In Kotlin, boolean property "shiny" has getter "getShiny" or "isShiny"
            try {
                getShinyMethod = pokemonClass.getMethod("getShiny");
            } catch (NoSuchMethodException e) {
                // Try Kotlin boolean property naming
                getShinyMethod = pokemonClass.getMethod("isShiny");
            }
            
            // Get species - returns a Species object
            getSpeciesMethod = pokemonClass.getMethod("getSpecies");
            
            // Get the name from Species
            Class<?> speciesClass = getSpeciesMethod.getReturnType();
            getSpeciesNameMethod = speciesClass.getMethod("getName");
            
        } catch (Exception e) {
            System.err.println("[Baritone] Failed to initialize Cobblemon reflection: " + e.getMessage());
            cobblemonPresent = false;
        }
    }

    /**
     * Check if an entity is a Cobblemon Pokemon entity.
     *
     * @param entity The entity to check
     * @return true if the entity is a Pokemon
     */
    public static boolean isPokemon(Entity entity) {
        if (!isCobblemonPresent() || entity == null) {
            return false;
        }
        return pokemonEntityClass.isInstance(entity);
    }

    /**
     * Check if a Pokemon entity is shiny.
     *
     * @param entity The Pokemon entity
     * @return true if shiny, false otherwise or if not a Pokemon
     */
    public static boolean isShiny(Entity entity) {
        if (!isPokemon(entity)) {
            return false;
        }
        try {
            Object pokemon = getPokemonMethod.invoke(entity);
            if (pokemon == null) {
                return false;
            }
            Object shinyResult = getShinyMethod.invoke(pokemon);
            return Boolean.TRUE.equals(shinyResult);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the species name of a Pokemon entity.
     *
     * @param entity The Pokemon entity
     * @return The species name in lowercase, or null if not a Pokemon
     */
    public static String getSpeciesName(Entity entity) {
        if (!isPokemon(entity)) {
            return null;
        }
        try {
            Object pokemon = getPokemonMethod.invoke(entity);
            if (pokemon == null) {
                return null;
            }
            Object species = getSpeciesMethod.invoke(pokemon);
            if (species == null) {
                return null;
            }
            Object name = getSpeciesNameMethod.invoke(species);
            return name != null ? name.toString().toLowerCase() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get all available Pokemon species names for tab completion.
     *
     * @return A set of species names in lowercase, or empty set if Cobblemon is not present
     */
    public static Set<String> getAllSpeciesNames() {
        if (speciesNamesCache != null) {
            return speciesNamesCache;
        }
        
        if (!isCobblemonPresent()) {
            return Collections.emptySet();
        }
        
        try {
            // Access CobblemonSpecies.INSTANCE.getSpecies() to get all species
            Class<?> speciesRegistryClass = Class.forName(COBBLEMON_SPECIES_CLASS);
            
            // Get the INSTANCE field (Kotlin object pattern)
            Object instance = speciesRegistryClass.getField("INSTANCE").get(null);
            
            // Get the getSpecies() method that returns the collection of all species
            Method getSpeciesMethod = speciesRegistryClass.getMethod("getSpecies");
            Object speciesCollection = getSpeciesMethod.invoke(instance);
            
            if (speciesCollection instanceof Collection) {
                Collection<?> species = (Collection<?>) speciesCollection;
                speciesNamesCache = species.stream()
                        .map(s -> {
                            try {
                                Method getNameMethod = s.getClass().getMethod("getName");
                                Object name = getNameMethod.invoke(s);
                                return name != null ? name.toString().toLowerCase() : null;
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(s -> s != null && !s.isEmpty())
                        .collect(Collectors.toSet());
                return speciesNamesCache;
            }
        } catch (Exception e) {
            // Log but don't fail - tab completion just won't work
            System.err.println("[Baritone] Failed to load Cobblemon species list: " + e.getMessage());
        }
        
        return Collections.emptySet();
    }

    /**
     * Check if a species name is valid.
     *
     * @param name The species name to check
     * @return true if the species exists
     */
    public static boolean isValidSpecies(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        Set<String> allSpecies = getAllSpeciesNames();
        // If we couldn't load the species list, allow any name
        if (allSpecies.isEmpty()) {
            return true;
        }
        return allSpecies.contains(name.toLowerCase());
    }

    /**
     * Clear the cached species names. Useful if mods are reloaded.
     */
    public static void clearCache() {
        speciesNamesCache = null;
    }
}

