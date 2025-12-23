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

package baritone.command.defaults;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.exception.CommandException;
import baritone.api.command.exception.CommandInvalidStateException;
import baritone.api.command.helpers.TabCompleteHelper;
import baritone.utils.CobblemonHelper;
import net.minecraft.world.entity.Entity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Command for hunting Cobblemon Pokemon entities.
 * Supports hunting all Pokemon, shiny Pokemon only, or specific species.
 *
 * Usage:
 * - #hunt - Hunt any nearby Pokemon
 * - #hunt shiny - Hunt only shiny Pokemon
 * - #hunt <species> - Hunt a specific species
 * - #hunt shiny <species> - Hunt shiny of a specific species
 *
 * @author Baritone Contributors
 */
public class HuntCommand extends Command {

    public HuntCommand(IBaritone baritone) {
        super(baritone, "hunt", "pokemon");
    }

    @Override
    public void execute(String label, IArgConsumer args) throws CommandException {
        // Check if Cobblemon is installed
        if (!CobblemonHelper.isCobblemonPresent()) {
            throw new CommandInvalidStateException("Cobblemon mod is not installed! The hunt command requires Cobblemon.");
        }

        boolean shinyOnly = false;
        String targetSpecies = null;

        // Parse arguments
        while (args.hasAny()) {
            String arg = args.getString().toLowerCase(Locale.US);
            
            if (arg.equals("shiny")) {
                shinyOnly = true;
            } else {
                // Assume it's a species name
                targetSpecies = arg;
            }
        }

        // Build the filter predicate
        final boolean filterShiny = shinyOnly;
        final String filterSpecies = targetSpecies;
        
        Predicate<Entity> filter = entity -> {
            // Must be a Pokemon
            if (!CobblemonHelper.isPokemon(entity)) {
                return false;
            }
            
            // Check shiny filter
            if (filterShiny && !CobblemonHelper.isShiny(entity)) {
                return false;
            }
            
            // Check species filter
            if (filterSpecies != null) {
                String speciesName = CobblemonHelper.getSpeciesName(entity);
                if (speciesName == null || !speciesName.equals(filterSpecies)) {
                    return false;
                }
            }
            
            return true;
        };

        // Start following/hunting Pokemon
        baritone.getFollowProcess().follow(filter);

        // Log what we're hunting
        StringBuilder message = new StringBuilder("Hunting ");
        if (filterShiny) {
            message.append("shiny ");
        }
        if (filterSpecies != null) {
            message.append(filterSpecies);
        } else {
            message.append("Pokemon");
        }
        message.append("...");
        
        logDirect(message.toString());
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) throws CommandException {
        if (!CobblemonHelper.isCobblemonPresent()) {
            return Stream.empty();
        }

        // Check what the user has already typed
        boolean hasShiny = false;
        String partial = "";
        
        while (args.has(2)) {
            String arg = args.getString().toLowerCase(Locale.US);
            if (arg.equals("shiny")) {
                hasShiny = true;
            }
        }
        
        if (args.hasAny()) {
            partial = args.getString();
        }

        TabCompleteHelper helper = new TabCompleteHelper();
        
        // If user hasn't typed "shiny" yet, suggest it
        if (!hasShiny) {
            helper.append("shiny");
        }
        
        // Add all species names
        Set<String> speciesNames = CobblemonHelper.getAllSpeciesNames();
        helper.append(speciesNames.stream());
        
        return helper
                .filterPrefix(partial)
                .sortAlphabetically()
                .stream();
    }

    @Override
    public String getShortDesc() {
        return "Hunt Cobblemon Pokemon";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "The hunt command automatically finds and paths to Cobblemon Pokemon.",
                "It uses Baritone's follow behavior to continuously track nearby Pokemon.",
                "",
                "Usage:",
                "> hunt - Hunt any nearby Pokemon",
                "> hunt shiny - Hunt only shiny Pokemon",
                "> hunt <species> - Hunt a specific species (e.g., 'hunt pikachu')",
                "> hunt shiny <species> - Hunt shiny of a specific species",
                "",
                "Use '#cancel' or '#stop' to stop hunting."
        );
    }
}

