package io.github.fishgames.athanorium.core.molecule;

import io.github.fishgames.athanorium.core.hex.Hex;
import io.github.fishgames.athanorium.core.id.Id;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A molecule as in Opus Magnum: an atom list with positions plus an explicit bond list.
 * Neighbouring atoms are NOT bonded automatically. A basic element is a molecule with a single atom.
 * Positions are relative to the molecule centre [0,0].
 */
public final class Molecule {
    public record Atom(Id element, Hex pos) {
    }

    /** Bond between two neighbouring positions; the direction of from/to carries no meaning. */
    public record Bond(Hex from, Hex to, Id type) {
        /** The same bond with from/to in a fixed order (smaller first). */
        public Bond normalized() {
            return compare(from, to) <= 0 ? this : new Bond(to, from, type);
        }
    }

    private final List<Atom> atoms;
    private final List<Bond> bonds;

    public Molecule(List<Atom> atoms, List<Bond> bonds) {
        this.atoms = List.copyOf(atoms);
        this.bonds = List.copyOf(bonds);
    }

    public List<Atom> atoms() {
        return atoms;
    }

    public List<Bond> bonds() {
        return bonds;
    }

    public static int compare(Hex a, Hex b) {
        int c = Integer.compare(a.q(), b.q());
        return c != 0 ? c : Integer.compare(a.r(), b.r());
    }

    /** Rotates the molecule around [0,0] by 60 degrees * steps counter-clockwise. */
    public Molecule rotated(int steps) {
        List<Atom> a = new ArrayList<>();
        for (Atom atom : atoms) {
            a.add(new Atom(atom.element(), atom.pos().rotateCcw(steps)));
        }
        List<Bond> b = new ArrayList<>();
        for (Bond bond : bonds) {
            b.add(new Bond(bond.from().rotateCcw(steps), bond.to().rotateCcw(steps), bond.type()));
        }
        return new Molecule(a, b);
    }

    /** True if both molecules have the same atoms, positions and bonds (order does not matter). */
    public boolean sameShape(Molecule other) {
        return canonicalAtoms().equals(other.canonicalAtoms()) && canonicalBonds().equals(other.canonicalBonds());
    }

    /**
     * True if {@code other}, rotated by the given steps, equals this molecule. A rotated molecule does not match
     * the unrotated one: this is how an output only accepts a molecule in exactly its own orientation.
     */
    public boolean sameShapeInRotation(Molecule other, int steps) {
        return sameShape(other.rotated(steps));
    }

    /** Atoms sorted by position. */
    public List<Atom> canonicalAtoms() {
        List<Atom> sorted = new ArrayList<>(atoms);
        sorted.sort(Comparator.comparing((Atom a) -> a.pos(), Molecule::compare).thenComparing(Atom::element));
        return sorted;
    }

    /** Bonds with normalized direction, sorted by position and type. */
    public List<Bond> canonicalBonds() {
        List<Bond> sorted = new ArrayList<>();
        for (Bond b : bonds) {
            sorted.add(b.normalized());
        }
        sorted.sort(Comparator.comparing((Bond b) -> b.from(), Molecule::compare)
                .thenComparing(b -> b.to(), Molecule::compare)
                .thenComparing(Bond::type));
        return sorted;
    }

    /** All atoms reachable from the first atom over bonds. */
    public boolean isConnected() {
        if (atoms.isEmpty()) {
            return false;
        }
        Map<Hex, List<Hex>> adjacency = new HashMap<>();
        for (Bond b : bonds) {
            adjacency.computeIfAbsent(b.from(), k -> new ArrayList<>()).add(b.to());
            adjacency.computeIfAbsent(b.to(), k -> new ArrayList<>()).add(b.from());
        }
        Set<Hex> seen = new HashSet<>();
        List<Hex> stack = new ArrayList<>();
        stack.add(atoms.get(0).pos());
        seen.add(atoms.get(0).pos());
        while (!stack.isEmpty()) {
            Hex h = stack.remove(stack.size() - 1);
            for (Hex n : adjacency.getOrDefault(h, List.of())) {
                if (seen.add(n)) {
                    stack.add(n);
                }
            }
        }
        return seen.size() == atoms.size();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Molecule m && sameShape(m);
    }

    @Override
    public int hashCode() {
        return canonicalAtoms().hashCode() * 31 + canonicalBonds().hashCode();
    }
}
