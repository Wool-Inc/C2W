/*
package net.klaaswhite.c2w.managers;

import net.klaaswhite.c2w.legacy.species.SpecieBarbarian;
import net.klaaswhite.c2w.legacy.species.SpecieBulwark;
import net.klaaswhite.c2w.legacy.species.SpecieMessenger;
import net.klaaswhite.c2w.legacy.species.SpecieStrongman;
import net.klaaswhite.c2w.interfaces.ISpecie;
import net.klaaswhite.c2w.interfaces.ISpecieManager;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;

public class SpecieManager implements ISpecieManager {

    private final Map<String, Class<? extends ISpecie>> _species;
    private final HashSet<String> _disabledSpecies;
    private final GameManager gameManager;

    SpecieManager(GameManager gameManager){
        this.gameManager = gameManager;

        _species = new Hashtable<>();
        _disabledSpecies = new HashSet<>();

        _species.put("messenger", SpecieMessenger.class);
        _species.put("bulwark", SpecieBulwark.class);
        _species.put("barbarian", SpecieBarbarian.class);
        _species.put("strongman", SpecieStrongman.class);
    }

    @Override
    public String[] getEnabledSpecieList() {
        ArrayList<String> ret = new ArrayList<>();
        for (String specieName : _species.keySet()){
            if (_disabledSpecies.contains(specieName)) continue;
            ret.add(specieName);
        }
        return ret.toArray(new String[0]);
    }

    @Override
    public String[] getDisabledSpecieList() {
        return _disabledSpecies.toArray(new String[0]);
    }

    @Override
    public void setPlayerSpecie(Player invoker, String playerName, String specieName) {
        specieName = specieName.toLowerCase();

        try {
            Class<? extends ISpecie> selectedSpecie = _species.get(specieName);
            if (selectedSpecie == null) {
                invoker.sendMessage(String.format("%s specie was not found", specieName));
                return;
            }

            if (_disabledSpecies.contains(specieName)) {
                invoker.sendMessage(String.format("%s is disabled", specieName));
                return;
            }

            invoker.sendMessage(String.format("Player %s was set to %s", playerName, specieName));

            var selectedSpecieCtr = selectedSpecie.getConstructor();
            var newSpecie = selectedSpecieCtr.newInstance();

            gameManager.getPlayerManager().setPlayerSpecie(gameManager.getPlayerManager().getPlayer(playerName), newSpecie);
        } catch (Exception e){
            System.out.println("Exception at setPlayerSpecie: " + e);
        }

    }

    @Override
    public void enableSpecie(Player invoker, String specie) {
        specie = specie.toLowerCase();
        Class<? extends ISpecie> selectedSpecie = _species.get(specie);
        if (selectedSpecie == null) {
            invoker.sendMessage(String.format("%s specie was not found", specie));
            return;
        }
        if (!_disabledSpecies.contains(specie)) {
            invoker.sendMessage(String.format("%s was not disabled", specie));
            return;
        }

        _disabledSpecies.remove(specie);
        invoker.sendMessage(String.format("Enabled %s", specie));
    }

    @Override
    public void disableSpecie(Player invoker, String specie) {
        specie = specie.toLowerCase();
        Class<? extends ISpecie> selectedSpecie = _species.get(specie);
        if (selectedSpecie == null) {
            invoker.sendMessage(String.format("%s specie was not found", specie));
            return;
        }
        if (_disabledSpecies.contains(specie)) {
            invoker.sendMessage(String.format("%s is already disabled", specie));
            return;
        }

        _disabledSpecies.add(specie);
        invoker.sendMessage(String.format("Disabled %s", specie));
    }
}
*/
