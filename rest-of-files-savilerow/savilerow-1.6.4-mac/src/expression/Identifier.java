package savilerow.expression;
/*

    Savile Row http://savilerow.cs.st-andrews.ac.uk/
    Copyright (C) 2014, 2015 Peter Nightingale
    
    This file is part of Savile Row.
    
    Savile Row is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    Savile Row is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    
    You should have received a copy of the GNU General Public License
    along with Savile Row.  If not, see <http://www.gnu.org/licenses/>.

*/

import savilerow.model.SymbolTable;
import savilerow.*;
import savilerow.model.*;

import java.util.*;
import java.io.*;

public class Identifier extends ASTNode {
    public static final long serialVersionUID = 1L;
    private String name;
    public SymbolTable global_symbols;

    public Identifier(String id, SymbolTable st) {
        super();
        name = id;
        global_symbols = st;
    }
    
    public String toString() {
        return name;
    }

    public ASTNode copy() {
        return new Identifier(name, global_symbols);
    }

    public String getName() {
        return name;
    }
    @Override
    public boolean equals(Object other) {
        if (! (other instanceof Identifier)) {
            return false;
        }
        return ((Identifier) other).name.equals(this.name);
    }
    
    @Override
    public int hashCode() {
        if(hashCache==Integer.MIN_VALUE) {
            int hash = this.name.hashCode();
            hashCache=hash;  // store
            return hash;
        }
        else {
            return hashCache;
        }
    }

    // Is it a bool or matrix of bool.
    public boolean isRelation() {
        if (global_symbols.hasVariable(name)) {
            return global_symbols.isRelational(name);
        } else {
            ASTNode dom = this.getDomainForId(this);
            if (dom instanceof BooleanDomain) {
                return true;
            }
            if (dom instanceof MatrixDomain && dom.getChild(0) instanceof BooleanDomain) {
                return true;
            }
            return false;
        }
    }
    public boolean isNumerical() {
        return !this.isRelation() && !this.isSet();
    }
    public boolean isSet() {
        // An identifier may be a set if there is a letting defining it as such.
        ArrayList<ASTNode> letgivs = new ArrayList<ASTNode>(global_symbols.lettings_givens);
        for (int i =0; i < letgivs.size(); i++) {
            if (letgivs.get(i) instanceof Letting) {
                if (letgivs.get(i).getChild(0).equals(this)) {
                    if (letgivs.get(i).getChild(1).isSet()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public int getCategory() {
        if (global_symbols.hasVariable(name)) {
            return global_symbols.getCategory(name);
        }

        // Go up tree to find quantifier.
        if (this.getDomainForId(this) != null) {
            return ASTNode.Quantifier;            // Could actually be a MatrixDomain, but the id originally came from a quantifier.
            // COULD be a AuxBubble that defined the variable!  Then it is wrong!
        }

        return ASTNode.Undeclared;
    }

    public boolean isAuxiliary() {
        return global_symbols.getCategories().get(name).cat == ASTNode.Auxiliary;
    }

    public int getDimension() {
        ASTNode dom = this.getDomainForId(this);
        if (dom == null) {
            dom = global_symbols.getDomain(name);
        }

        if (dom instanceof MatrixDomain) {
            return dom.numChildren() - 3;
        }
        if (dom instanceof HoleyMatrixDomain) {
            return dom.getChild(0).numChildren() - 3;
        }

        return 0;
    }

    // For indexable expressions, return the domain of each dimension.
    public ArrayList<ASTNode> getIndexDomains() {
        if (getDimension() == 0) {
            return null;
        }
        ASTNode dom = this.getDomainForId(this);
        if (dom == null) {
            dom = global_symbols.getDomain(name);
        }

        if (dom instanceof MatrixDomain) {
            ArrayList<ASTNode> idxdoms = dom.getChildren();
            idxdoms.remove(0); idxdoms.remove(0); idxdoms.remove(0);
            return idxdoms;
        }
        assert false;        // Should have found a matrixdomain.
        return null;
    }

    public boolean typecheck(SymbolTable st) {
        // Checks if the identifier is defined.
        // Unfortunately shares a lot of code with method above.
        assert st == global_symbols;        // If this isn't true, we have two symbol tables floating around. Very strange.
        if (global_symbols.hasVariable(name)) {
            return true;
        }
        if (this.getDomainForId(this) != null) {
            return true;            // Found domain above, in a quantifier or matrixdomain
        }

        System.out.println("ERROR: Identifier not defined: " + this);
        return false;
    }
    @Override
    public ASTNode simplify() {
        ASTNode rep = global_symbols.replacements.get(this);        // Has this symbol been replaced?
        if (rep != null) {
            return rep.copy();
        }
        
        return this;
    }
    
    // Get the full domain for this identifier.
    public ASTNode getDomain() {
        ASTNode d = global_symbols.getDomain(name);
        if(d==null) {
            d=this.getDomainForId(this);
        }
        assert d!=null;
        return d;
    }
    
    public Intpair getBounds() {

        // Also needs to check for quantifiers
        ASTNode d = getDomain();
        if (d instanceof MatrixDomain) {
            d = d.getChild(0);
        }

        return d.getBounds();
    }
    
    // Returns a parameter expression. If this is a quantifier id,
    public PairASTNode getBoundsAST() {
        // Also needs to check for quantifiers
        int cat = this.getCategory();

        if (cat == ASTNode.Undeclared) {
            System.out.println(this);
        }
        assert cat != ASTNode.Undeclared;

        if (cat == ASTNode.Parameter || cat == ASTNode.Constant) {
            // If this is parameter p, then its bounds are p..p
            ASTNode dom = global_symbols.getDomain(name);
            if (dom instanceof SimpleDomain) {
                return new PairASTNode(this, this);
            } else if (dom instanceof MatrixDomain) {
                return dom.getChild(0).getBoundsAST();
            } else {
                System.out.println("Strange parameter type in Identifier.java");
                assert false;
                return new PairASTNode(this, this);
            }
        }
        if (cat == ASTNode.Quantifier) {
            ASTNode d = this.getDomainForId(this);

            assert d != null;
            if (d instanceof MatrixDomain) {
                d = d.getChild(0);
            }

            PairASTNode p = d.getBoundsAST();

            // p may still contain some other quantifier id.

            while (p.e1.getCategory() > ASTNode.Parameter) {
                p.e1 = p.e1.getBoundsAST().e1;
            }


            while (p.e2.getCategory() > ASTNode.Parameter) {
                p.e2 = p.e2.getBoundsAST().e2;
            }


            return p;
        }

        assert cat == ASTNode.Decision;
        // If it's a decision variable, the bounds come from the domain.
        ASTNode d = global_symbols.getDomain(name);
        if (d instanceof MatrixDomain) {
            d = d.getChild(0);
        }
        return d.getBoundsAST();
    }

    // Some methods for propagators to use -- only contained in Identifier, although later other things such as negation could implement these.
    // Returned boolean indicates whether the domain changed.
    public boolean setBounds(long lower, long upper) {
        ArrayList<Intpair> a = global_symbols.getDomain(name).getIntervalSet();
        ArrayList<Intpair> b = new ArrayList<Intpair>();

        boolean changed = false;        // Has the domain changed.

        Intpair newbnds = new Intpair(lower, upper);

        for (int aloc =0; aloc < a.size(); aloc++) {
            Intpair inter = newbnds.intersect(a.get(aloc));

            if (inter.equals(a.get(aloc))) {
                b.add(inter);
            } else {
                changed = true;
                if (! inter.isEmpty()) {
                    b.add(inter);
                }
            }

        }

        if (changed) {
            // Replace a booleandomain with a booleandomain
            boolean isBool = global_symbols.getDomain(name) instanceof BooleanDomain;
            global_symbols.setDomain(name, Intpair.makeDomain(b, isBool));
        }

        return changed;
    }

    public void toMinion(StringBuffer b, boolean bool_context) {
        if (bool_context) {
            // Write a constraint
            if (CmdFlags.getUseBoundVars() && this.exceedsBoundThreshold()) {
                b.append("eq(");
            } else {
                b.append("w-literal(");
            }
            b.append(name);
            b.append(",1)");
        } else {
            b.append(name);
        }
    }

    public void toDominionInner(StringBuffer b, boolean bool_context) {
        if (bool_context) {
            // Print out a constraint
            b.append(CmdFlags.getCtName() + " ");
            b.append("literal(");
            b.append(name);
            b.append(",1)");
        } else {
            // Just the name
            b.append(name);
        }
    }
    public void toDominionParam(StringBuffer b) {
        b.append(name);
    }
    public void toFlatzinc(StringBuffer b, boolean bool_context) {
        ASTNode dom=global_symbols.getDomain(name);
        if (global_symbols.hasVariable(name) && (dom instanceof BooleanDomain || dom.equals(new IntegerDomain(new Range(new NumberConstant(0), new NumberConstant(1)))))) {
            if (bool_context) {
                b.append(name + "_BOOL");
            }
            else {
                b.append(name + "_INTEGER");
            }
        }
        else {
            b.append(name);
        }
    }
    
    public void toMinizinc(StringBuffer b, boolean bool_context) {
        ASTNode dom=global_symbols.getDomain(name);
        if (global_symbols.hasVariable(name) && (dom instanceof BooleanDomain || dom.equals(new IntegerDomain(new Range(new NumberConstant(0), new NumberConstant(1)))))) {
            if (bool_context) {
                b.append(name + "_BOOL");
            } else {
                b.append(name + "_INTEGER");
            }
        } else {
            b.append(name);
        }
    }
    
    public long directEncode(Sat satModel, long value) {
        return satModel.getDirectVariable(name, value);
    }
    public long orderEncode(Sat satModel, long value) {
        return satModel.getOrderVariable(name, value);
    }
    
    public Long toSATLiteral(Sat satModel) {
        if(isRelation()) {
            return satModel.getDirectVariable(name, 1);
        }
        else return null;
    }
    public void toSATWithAuxVar(Sat satModel, long auxVarValue) throws IOException
    {
        long identifierValue=satModel.getDirectVariable(name, 1);
        
        satModel.addClause((-auxVarValue) + " " + (identifierValue));
        satModel.addClause((auxVarValue) + " " + (-identifierValue));
    }
    
    public void toJSON(StringBuffer bf) {
        //   Just the name with a $ in front. 
        bf.append("\"$" + name + "\"\n");
    }
}
