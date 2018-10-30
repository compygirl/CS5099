package savilerow.model;
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

import savilerow.CmdFlags;
import savilerow.expression.*;
import savilerow.treetransformer.*;
import savilerow.*;

import java.util.*;
import java.lang.Math;
import java.io.*;

// Very straightforward implementation of a symbol table that maps
// variables (looked up by name) to their domain and category (parameter or decision)
// Quantifier variables are not included here, only global ones.

public class SymbolTable implements Serializable {
    private static final long serialVersionUID = 1L;

    public Model m;

    public ArrayDeque<ASTNode> lettings_givens;    // Lettings, givens, wheres, finds in order.

    // Category has an entry for each symbol.
    public HashMap<String, categoryentry> category;

    public categoryentry category_first;
    public categoryentry category_last;

    
    // Arraylist version of the above for serialization.
    private ArrayList<categoryentry> category_list;
    
    // domains and constant_matrices have entries whenever the symbol has an
    // associated domain and/or constant matrix
    
    // Domain could be an identifier -- should be defined in a  letting.
    private HashMap<String, ASTNode> domains;
    private HashMap<String, ASTNode> constant_matrices;
    
    public HashMap<String, String> represents_ct;    // String representation of the ct the aux var represents.
    
    // Yet another data structure -- for matrices that have been replaced with
    // atomic variables. This is the domain after shift to 0
    public HashMap<String, ASTNode> deleted_matrices;
    
    // And yet another. This one is the original domain of matrices, stored before
    // the indices are shifted to 0.
    public HashMap<String, ASTNode> matrix_original_domain;
    
    public HashMap<String, replaces_matrix_entry> replaces_matrix;    // as in replaces["M___5___4"]=<"M", [5,4]>
    
    int auxvarcounter;
    
    public HashMap<ASTNode, ASTNode> replacements;    // Variables that have been deleted and replaced with either
    // another variable, a constant or a simple expression like a negation of a variable. 
    public HashMap<ASTNode, ASTNode> replacements_domains;    // Domains for deleted vars at the point of deletion.
    public HashMap<ASTNode, Integer> replacements_category;    // Category of deleted vars at point of deletion.
    
    //  Special hash-tables for marking variables as bool, int or both. 
    HashMap<String, Boolean> boolvar_bool;    // Not included in .equals comparison or copy.
    HashMap<String, Boolean> boolvar_int;
    
    //   When encoding to SAT, some variables marked as needing direct encoding. 
    HashSet<String> directvar_sat;
    
    public SymbolTable() {
        lettings_givens = new ArrayDeque<ASTNode>();
        domains = new HashMap<String, ASTNode>();

        // This is the ordering on the symbols for output.
        category = new HashMap<String, categoryentry>();
        category_first = null;
        category_last = null;

        constant_matrices = new HashMap<String, ASTNode>();
        represents_ct = new HashMap<String, String>();
        auxvarcounter = 0;

        // Extra data for gecode and minizinc output.
        boolvar_bool = new HashMap<String, Boolean>();
        boolvar_int = new HashMap<String, Boolean>();

        deleted_matrices = new HashMap<String, ASTNode>();
        matrix_original_domain = new HashMap<String, ASTNode>();
        replaces_matrix = new HashMap<String, replaces_matrix_entry>();

        replacements = new HashMap<ASTNode, ASTNode>();
        replacements_domains = new HashMap<ASTNode, ASTNode>();
        replacements_category = new HashMap<ASTNode, Integer>();
    }
    
    private void category_put_end(String name, int cat) {
        if (category_last == null) {
            assert category_first == null;
            category_first = category_last = new categoryentry(name, cat, null, null);
        } else {
            categoryentry tmp = new categoryentry(name, cat, category_last, null);
            category_last.next = tmp;
            category_last = tmp;
        }
        category.put(name, category_last);
    }
    
    @Override
    public boolean equals(Object b) {
        if (! (b instanceof SymbolTable)) {
            return false;
        }
        SymbolTable c = (SymbolTable) b;

        // Omitting m.

        // Irritatingly ArrayDeque does not have its own .equals.
        if (lettings_givens.size() != c.lettings_givens.size()) {
            return false;
        }

        Iterator<ASTNode> it1 = lettings_givens.iterator();
        Iterator<ASTNode> it2 = c.lettings_givens.iterator();
        while (it1.hasNext()) {
            if (! it1.next().equals(it2.next())) {
                return false;
            }
        }

        if (! c.category.equals(category)) {
            return false;
        }
        // Iterate down the categoryentry list checking equality. Can't do this recursively because it blows the stack.
        categoryentry iter_this = category_first;
        categoryentry iter_other = c.category_first;
        while (iter_this != null || iter_other != null) {
            if (iter_this == null || iter_other == null) {
                // One is null and the other is not.
                return false;
            }

            if (! iter_this.equals(iter_other)) {
                return false;
            }

            assert iter_this.next == null || iter_this.next.prev == iter_this;
            assert iter_other.next == null || iter_other.next.prev == iter_other;
            assert iter_this.next != null || iter_this == category_last;
            assert iter_other.next != null || iter_other == c.category_last;

            iter_this = iter_this.next;
            iter_other = iter_other.next;
        }

        if (! c.domains.equals(domains)) {
            return false;
        }
        if (! c.constant_matrices.equals(constant_matrices)) {
            return false;
        }
        if (! c.represents_ct.equals(represents_ct)) {
            return false;
        }
        
        if (! c.deleted_matrices.equals(deleted_matrices)) {
            return false;
        }
        if (! c.matrix_original_domain.equals(matrix_original_domain)) {
            return false;
        }
        if (! c.replaces_matrix.equals(replaces_matrix)) {
            return false;
        }
        if (c.auxvarcounter != auxvarcounter) {
            return false;
        }

        if (! c.replacements.equals(replacements)) {
            return false;
        }
        if (! c.replacements_domains.equals(replacements_domains)) {
            return false;
        }
        if (! c.replacements_category.equals(replacements_category)) {
            return false;
        }

        return true;
    }
    
    public SymbolTable copy() {
        SymbolTable st = new SymbolTable();
        
        TransformFixSTRef tf = new TransformFixSTRef(st);
        
        // Copy lettings, givens etc in sequence.
        for (Iterator<ASTNode> itr = lettings_givens.iterator(); itr.hasNext();) {
            ASTNode letgiv = itr.next();
            st.lettings_givens.addLast(tf.transform(letgiv.copy()));
        }
        
        categoryentry cur = category_first;
        while (cur != null) {
            st.category_put_end(cur.name, cur.cat);
            cur = cur.next;
        }
        
        for (String domst : domains.keySet()) {
            st.domains.put(domst, tf.transform(domains.get(domst).copy()));
        }
        for (String matst : constant_matrices.keySet()) {
            st.constant_matrices.put(matst, tf.transform(constant_matrices.get(matst).copy()));
        }
        st.represents_ct = new HashMap<String, String>(represents_ct);
        for (String delst : deleted_matrices.keySet()) {
            st.deleted_matrices.put(delst, tf.transform(deleted_matrices.get(delst).copy()));
        }
        for (String matdom : matrix_original_domain.keySet()) {
            st.matrix_original_domain.put(matdom, tf.transform(matrix_original_domain.get(matdom).copy()));
        }
        for (String repmat : replaces_matrix.keySet()) {
            replaces_matrix_entry r1 = new replaces_matrix_entry(replaces_matrix.get(repmat).name, new ArrayList<Long>(replaces_matrix.get(repmat).idx));
            st.replaces_matrix.put(repmat, r1);
        }
        st.auxvarcounter = auxvarcounter;
        
        for (ASTNode rep1 : replacements.keySet()) {
            st.replacements.put(tf.transform(rep1.copy()), tf.transform(replacements.get(rep1).copy()));
        }
        for (ASTNode rep2 : replacements_domains.keySet()) {
            st.replacements_domains.put(tf.transform(rep2.copy()), tf.transform(replacements_domains.get(rep2).copy()));
        }
        for (ASTNode rep3 : replacements_category.keySet()) {
            st.replacements_category.put(tf.transform(rep3.copy()), (int) replacements_category.get(rep3));
        }
        
        return st;
    }

    public void setModel(Model _m) { m = _m; }

    // To add parameters
    public void newVariable(String name, ASTNode dom, int cat) {
        assert ! category.containsKey(name);
        domains.put(name, dom);
        category_put_end(name, cat);
    }

    // For adding (at present) only constants defined by a letting.
    // Prevents multiple lettings for the same identifier.
    public void newVariable(String name, int cat) {
        assert cat == ASTNode.Constant;
        assert ! category.containsKey(name);
        category_put_end(name, cat);
    }

    // To add variables replacing a matrix
    public void newVariable(String name, ASTNode dom, int cat, ASTNode replaces, ArrayList<Long> indices) {
        assert ! category.containsKey(name);
        domains.put(name, dom);
        if (dom.getCategory() == ASTNode.Constant) {
            ArrayList<Intpair> set = dom.getIntervalSet();
            if (set.size() == 0) {
                CmdFlags.println("ERROR: Empty domain");
            }
        }
        categoryentry c = category.get(replaces.toString());        // Add directly before this

        categoryentry newcat = new categoryentry(name, cat, c.prev, c);

        // stitch it in
        if (newcat.next == null) {
            category_last = newcat;
        } else {
            newcat.next.prev = newcat;
        }
        if (newcat.prev == null) {
            category_first = newcat;
        } else {
            newcat.prev.next = newcat;
        }

        category.put(name, newcat);

        replaces_matrix.put(name, new replaces_matrix_entry(replaces.toString(), new ArrayList<Long>(indices)));
    }

    //////////////////////////////////////////////////////////////////////////// 
    // Unify two equal decision variables.

    public void unifyVariables(ASTNode id1, ASTNode id2) {
        assert id1.getCategory() == ASTNode.Decision && id2.getCategory() == ASTNode.Decision;
        
        // If one is an aux var and the other isn't, the aux var should be deleted.
        if (category.get(id1.toString()).cat == ASTNode.Auxiliary) {
            // Swap.
            ASTNode temp = id1;
            id1 = id2;
            id2 = temp;
        }
        // If there is a branchingon list (and now there always is), it doesn't matter which one is eliminated.
        
        TransformSimplify ts = new TransformSimplify();
        
        // This one can make an int find variable bool -- and mess up the solution output. 
        //setDomain(id1.toString(), Intpair.intersectPreservingBool(getDomain(id1.toString()), getDomain(id2.toString())));
        
        // This version can presumably make a boolean find variable into an int, and thus also mess up the output. 
        setDomain(id1.toString(), ts.transform(new Intersect(getDomain(id1.toString()), getDomain(id2.toString()))));
        
        // id2 will be replaced by id1.
        replacements.put(id2, id1);
        // this hash table will be used to find its value.
        replacements_domains.put(id2, getDomain(id2.toString()));
        replacements_category.put(id2, category.get(id2.toString()).cat);

        // get rid of id2 in Symboltable. Don't need to worry about
        // branchingon or other places because it will be done using ReplaceASTNode.
        deleteSymbol(id2.toString());
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    //   Unify two decision variables when one is the negation of the other.
    
    public void unifyVariablesNegated(ASTNode id1, ASTNode id2) {
        assert id1.getCategory() == ASTNode.Decision && id2.getCategory() == ASTNode.Decision;
        assert id2 instanceof Negate;
        id2=id2.getChild(0);  // Strip off the negation.
        
        assert id1.isRelation() && id2.isRelation();
        
        // If one is an aux var and the other isn't, the aux var should be deleted.
        if (category.get(id1.toString()).cat == ASTNode.Auxiliary) {
            // Swap.
            ASTNode temp = id1;
            id1 = id2;
            id2 = temp;
        }
        
        // If there is a branchingon list (and now there always is), it doesn't matter which one is eliminated.
        
        TransformSimplify ts = new TransformSimplify();

        // In some strange cases one or other might be assigned or empty, so intersect the domains. 
        setDomain(id1.toString(), ts.transform(new Intersect(getDomain(id1.toString()), getDomain(id2.toString()))));

        // id2 will be replaced by not id1.
        replacements.put(id2, new Negate(id1));
        // this hash table will be used to find its value.
        replacements_domains.put(id2, getDomain(id2.toString()));
        replacements_category.put(id2, category.get(id2.toString()).cat);

        // get rid of id2 in Symboltable. Don't need to worry about
        // branchingon or other places because it will be done using ReplaceASTNode.
        deleteSymbol(id2.toString());
    }
    
    //////////////////////////////////////////////////////////////////////////// 
    // 
    // Delete variable when it is assigned.

    public void assignVariable(ASTNode id, ASTNode value) {
        assert (!CmdFlags.getUseAggregate()) || CmdFlags.getAfterAggregate();
        assert id instanceof Identifier;
        assert value.isConstant();
        assert getDomain(id.toString()).containsValue(value.getValue());
        assert ((Identifier)id).global_symbols==this;
        
        if(id.isRelation()) {
            if(!value.isRelation()) {
                long v=value.getValue();
                assert v>=0 && v<=1;
                value=new BooleanConstant(v==1);
            }
        }
        
        replacements.put(id, value);        // This will be used to retrieve the value when parsing solver output.
        replacements_domains.put(id, getDomain(id.toString()));
        replacements_category.put(id, category.get(id.toString()).cat);
        
        deleteSymbol(id.toString());
    }

    //////////////////////////////////////////////////////////////////////////// 
    // 
    // Holey matrices

    // To add dim statements
    public void newDim(String name, ASTNode dom, int dim) {
        assert ! category.containsKey(name);
        ArrayList<ASTNode> ls = new ArrayList<ASTNode>();
        for (int i =0; i < dim; i++) {
            ls.add(new IntegerDomain(new Range(null, null)));
        }
        
        domains.put(name, new MatrixDomain(dom, ls));
        category_put_end(name, ASTNode.Dim);
    }

    // Add a forall-find, it should match up with a dim statement.
    public void newForallFind(String name, ASTNode forallfind) {
        assert domains.containsKey(name);        // must contain the dim statement already.
        domains.put(name, new HoleyMatrixDomain(domains.get(name), forallfind));
        category.get(name).cat = ASTNode.Decision;
    }

    //////////////////////////////////////////////////////////////////////////// 
    // 
    // Constant matrices

    public void newConstantMatrix(String name, ASTNode mat) {
        // System.out.println("Adding constant matrix: "+name+" with NO DOMAIN SPECIFIED matrix is:"+mat);
        
        if (category.containsKey(name)) {            // Should be a parameter...
            assert category.get(name).cat == ASTNode.Parameter;
            // shoudl check here that the dimensions/entries match the domain in the given.
            // System.out.println("Domain already in symbol table: "+domains.get(name));

            // Make it a constant matrix
            category.get(name).cat = ASTNode.ConstantMatrix;
        } else {
            category_put_end(name, ASTNode.ConstantMatrix);
        }

        constant_matrices.put(name, mat);

        // Leaves the domain (presumably from the given) in place, if there is one.

        if (domains.get(name) == null) {
            // There is no domain from a given/letting. It was something like 'letting vals = [1,2,3]'
            // Construct the matrix domain.
            ArrayList<Long> dim = getConstantMatrixSize(mat);
            Intpair cont = getConstantMatrixBounds(mat);
            ASTNode basedom;
            if (cont == null) {
                if(mat.isRelation()) {
                    basedom = new BooleanDomain(new Range(new NumberConstant(0), new NumberConstant(0)));
                }
                else {
                    basedom = new IntegerDomain(new Range(new NumberConstant(0), new NumberConstant(0)));
                }
            } else {
                if(mat.isRelation()) {
                    basedom = new BooleanDomain(new Range(new NumberConstant(cont.lower), new NumberConstant(cont.upper)));
                }
                else {
                    basedom = new IntegerDomain(new Range(new NumberConstant(cont.lower), new NumberConstant(cont.upper)));
                }
            }
            
            ASTNode cm = mat;
            ArrayList<ASTNode> indices = new ArrayList<ASTNode>();
            for (int i =0; i < dim.size(); i++) {
                indices.add(cm.getChild(0));
                if (cm.numChildren() > 1) {
                    cm = cm.getChild(1);
                } else {
                    assert i == dim.size() - 1;
                }
            }
            ASTNode matrixdom = new MatrixDomain(basedom, indices);

            domains.put(name, matrixdom);
            // CmdFlags.println(matrixdom);
        } else {
            // There is a domain from a previous given. Tighten the base domain.
            tightenConstantMatrixDomain(name);
        }
    }

    void tightenConstantMatrixDomain(String name) {
        Intpair matbnds = getConstantMatrixBounds(constant_matrices.get(name));

        if (matbnds != null) {
            // Matrix is not empty, therefore it has bounds, tighten the base domain.
            
            ASTNode basedom = domains.get(name).getChild(0);
            
            if (! (basedom.isBooleanSet())) {
                // If basedom were boolean, the intersect might change it to int. Can't have that.
                basedom = new Intersect(basedom, new IntegerDomain(new Range(new NumberConstant(matbnds.lower), new NumberConstant(matbnds.upper))));
                TransformSimplify ts = new TransformSimplify();
                basedom = ts.transform(basedom);
            }

            domains.get(name).setChild(0, basedom);
        }
    }

    ArrayList<Long> getConstantMatrixSize(ASTNode mat) {
        ArrayList<ASTNode> idxdoms=mat.getIndexDomains();
        
        ArrayList<Long> out=new ArrayList<Long>(idxdoms.size());
        
        for(int i=0; i<idxdoms.size(); i++) {
            // Might need to simplify here.
            
            ArrayList<Intpair> intervals=idxdoms.get(i).getIntervalSet();
            long size=0;
            
            for(int j=0; j<intervals.size(); j++) {
                size=size+intervals.get(j).upper-intervals.get(j).lower+1L;
            }
            out.add(size);
        }
        return out;
        /*
        Old version -- does not work with EmptyMatrix
        // Just looks at first entries if they exist.
        ArrayList<Integer> sizes = new ArrayList<Integer>();
        while (mat instanceof CompoundMatrix) {
            sizes.add(mat.numChildren() - 1);            // drop the index entry
            if (sizes.get(sizes.size() - 1) == 0) {
                break;
            }
            mat = mat.getChild(1);
        }
        return sizes;
        */
    }

    ArrayList<Long> getConstantMatrixContents(ASTNode mat) {
        ArrayList<Long> tmp = new ArrayList<Long>();
        if (mat instanceof CompoundMatrix || mat instanceof EmptyMatrix) {
            for (int i =1; i < mat.numChildren(); i++) {
                tmp.addAll(getConstantMatrixContents(mat.getChild(i)));
            }
        } else {
            // Should be a constant
            assert mat.isConstant();
            tmp.add(mat.getValue());
        }
        return tmp;
    }

    // Get max and min of the values in a compound matrix/empty matrix.
    Intpair getConstantMatrixBounds(ASTNode mat) {
        if (mat instanceof CompoundMatrix) {
            Intpair a = null;

            for (int i =1; i < mat.numChildren(); i++) {
                Intpair b = getConstantMatrixBounds(mat.getChild(i));
                if (b != null) {
                    if (a == null) {
                        a = b;
                    } else {
                        a.lower = (a.lower < b.lower) ? a.lower : b.lower;
                        a.upper = (a.upper > b.upper) ? a.upper : b.upper;
                    }
                }
            }
            return a;
        } else if (mat instanceof EmptyMatrix) {
            return null;            // nothing here.
        } else {
            // Should be a constant
            assert mat.isConstant();
            Intpair a = new Intpair(mat.getValue(), mat.getValue());
            return a;
        }
    }

    // Makes lettings where a given (or letting--really??) statement has 'matrix indexed by [int(a..b)...]'
    // and we don't know a and b, but can determine it from the index domains of the letting/param constant matrix.
    public ArrayList<ASTNode> makeLettingsConstantMatrix(String matname) {
        ASTNode mat = constant_matrices.get(matname);
        ASTNode dom = domains.get(matname);

        ArrayList<ASTNode> idxdoms = mat.getIndexDomains();

        ASTNode basedom = dom.getChild(0);

        ArrayList<ASTNode> indexdoms = dom.getChildren();        // get index domains.
        indexdoms.remove(0); indexdoms.remove(0); indexdoms.remove(0);

        ArrayList<ASTNode> newlettings = new ArrayList<ASTNode>();
        for (int i =0; i < indexdoms.size(); i++) {
            if (indexdoms.get(i) instanceof IntegerDomain) {
                if (indexdoms.get(i).numChildren() == 1 && idxdoms.get(i).numChildren() == 1) {
                    // Check if it is of type a..b

                    ASTNode range = indexdoms.get(i).getChild(0);
                    if (range instanceof Range && idxdoms.get(i).getChild(0) instanceof Range) {
                        if (range.getChild(0) instanceof Identifier) {
                            // a..something

                            // Old version -- use actual lower bound of idxdoms.get(i)
                            // ASTNode newlet=new Letting(range.getChild(0), idxdoms.get(i).getChild(0).getChild(0));

                            // New version -- compute a lower bound from the size of idxdoms.get(i)
                            ASTNode newlet = new Letting(range.getChild(0), BinOp.makeBinOp("+", BinOp.makeBinOp("-", idxdoms.get(i).getChild(0).getChild(0), idxdoms.get(i).getChild(0).getChild(1)), range.getChild(1)));
                            newlet = (new TransformSimplify()).transform(newlet);
                            newlettings.add(newlet);
                        }

                        if (range.getChild(1) instanceof Identifier) {
                            // something..b

                            // Old version
                            // ASTNode newlet=new Letting(range.getChild(1), idxdoms.get(i).getChild(0).getChild(1));

                            // New version -- compute a upper bound from the size of idxdoms.get(i)
                            ASTNode newlet = new Letting(range.getChild(1), BinOp.makeBinOp("+", BinOp.makeBinOp("-", idxdoms.get(i).getChild(0).getChild(1), idxdoms.get(i).getChild(0).getChild(0)), range.getChild(0)));
                            newlet = (new TransformSimplify()).transform(newlet);
                            newlettings.add(newlet);
                        }

                    }
                }
            }
        }
        return newlettings;
    }

    public void correctIndicesConstantMatrix(String matname) {
        // Uses the method below to correct the indices of the matrix literal
        // to line up with the domain (that may have come from a given, and
        // therefore may be different to the domain in a letting or the index domains in the matrix literal).
        ASTNode mat = constant_matrices.get(matname);
        ASTNode dom = domains.get(matname);

        Pair<ASTNode, Boolean> p = fixIndicesConstantMatrix(dom, mat);

        if (p.getSecond()) {
            System.out.println("WARNING: Index domains do not match for the matrix " + matname);
            System.out.println("WARNING: This could be a mismatch between the matrix given in the parameter file");
            System.out.println("WARNING: and its matrix domain in the given statement in the model file.");
            // System.out.println(mat);
            // System.out.println(dom);
        }

        constant_matrices.put(matname, p.getFirst());
    }

    // Correct the indices in CMs to match a matrix domain.
    // For a constant matrix given in a parameter file, this is first
    // applied for the domain in the letting (if there is one).
    // Then it's applied a second time for the domain in the given.
    public static Pair<ASTNode, Boolean> fixIndicesConstantMatrix(ASTNode matdom, ASTNode mat) {
        // Returns a new constant matrix with fixed domains, and a boolean saying whether
        // any of the CM domains have in fact changed.
        // It also changes mat in place in some cases.

        if (mat instanceof EmptyMatrix) {
            // Make matdom the domain inside the EmptyMatrix.
            // At the moment there is no way of specifying the domain inside an EmptyMatrix in the input lang,
            // so set the boolean to false (i.e. do not generate a warning) for fixing this.

            return new Pair<ASTNode, Boolean>(new EmptyMatrix(matdom.copy()), false);
        } else {
            assert mat instanceof CompoundMatrix;

            boolean ischanged = false;

            ASTNode cmindex = mat.getChild(0);
            ASTNode matdomindex = matdom.getChild(3);



            if (! cmindex.equals(matdomindex)) {
                mat.setChild(0, matdomindex);
                ischanged = true;
            }

            // Make a new matrix domain with one less index.
            ArrayList<ASTNode> inneridxdoms = matdom.getChildren();
            inneridxdoms.remove(0); inneridxdoms.remove(0); inneridxdoms.remove(0); inneridxdoms.remove(0);
            if (inneridxdoms.size() == 0) {
                return new Pair<ASTNode, Boolean>(mat, ischanged);
            }
            
            for (int i =1; i < mat.numChildren(); i++) {
                ASTNode innermatdom = new MatrixDomain(matdom.getChild(0), inneridxdoms);
                
                Pair<ASTNode, Boolean> p = fixIndicesConstantMatrix(innermatdom, mat.getChild(i));
                
                mat.setChild(i, p.getFirst());
                ischanged = ischanged || p.getSecond();
            }
            
            // In this branch mat has been changed in place.
            return new Pair<ASTNode, Boolean>(mat, ischanged);
        }
    }
    
    // This is called as part of type checking to check
    // the dimensions and base domain of the constant matrices.
    public boolean checkConstantMatrices() {
        for (String name : constant_matrices.keySet()) {
            ASTNode matdom = domains.get(name);
            ASTNode basedom = matdom.getChild(0);
            ArrayList<ASTNode> indexdoms = matdom.getChildren();
            indexdoms.remove(0); indexdoms.remove(0); indexdoms.remove(0);
            HashSet<Long> basedomset = null;
            if (basedom.isFiniteSet()) {
                TransformSimplify ts = new TransformSimplify();
                basedomset = new HashSet<Long>(ts.transform(basedom).getValueSet());
            }
            if (!checkConstantMatrixDomain(name, constant_matrices.get(name), indexdoms, basedomset, 0)) {
                return false;
            }
        }
        return true;
    }

    private boolean checkConstantMatrixDomain(String name, ASTNode mat, ArrayList<ASTNode> indexdoms, HashSet<Long> basedom, int index) {
        ASTNode indexdom = indexdoms.get(index);
        // Check length of mat.
        TransformSimplify ts = new TransformSimplify();
        if (indexdom.isFiniteSet()) {
            int size = ts.transform(indexdom).getValueSet().size();
            if (mat.numChildren() - 1 != size) {
                CmdFlags.println("ERROR: At index " + index + " of constant matrix " + name + ", actual size does not match the matrix dimensions.");
                return false;
            }
        } else {
            CmdFlags.println("ERROR: Not allowed infinite set (" + indexdom + ") for index " + index + " of matrix " + name);
            return false;
        }

        if (index == indexdoms.size() - 1) {
            // Base case -- Check values are in the base domain
            if (basedom != null) {
                for (int i =1; i < mat.numChildren(); i++) {
                    ASTNode a = mat.getChild(i);
                    if (!basedom.contains(a.getValue())) {
                        CmdFlags.println("ERROR: Item " + a + " is not contained in domain of constant matrix " + name + ".");
                        return false;
                    }
                }
            }
        } else {
            // Recursive case.
            for (int i =1; i < mat.numChildren(); i++) {
                ASTNode a = mat.getChild(i);
                if (!checkConstantMatrixDomain(name, (CompoundMatrix) a, indexdoms, basedom, index + 1)) {
                    return false;
                }
            }
        }
        return true;
    }

    public ASTNode getDomain(String varid) {
        return domains.get(varid);
    }

    public ASTNode getConstantMatrix(String varid) {
        assert constant_matrices.containsKey(varid);
        return constant_matrices.get(varid);
    }
    public void setConstantMatrix(String varid, ASTNode cm) {
        assert constant_matrices.containsKey(varid);
        constant_matrices.put(varid, cm);
    }
    
    public void setDomain(String varid, ASTNode d) {
        if (d instanceof MatrixDomain && !matrix_original_domain.containsKey(varid)) {
            matrix_original_domain.put(varid, domains.get(varid));            // Keep the first domain a matrix has.
        }
        domains.put(varid, d);
    }
    
    public ASTNode getOriginalDomain(String varid) {
        return matrix_original_domain.get(varid);
    }

    public HashMap<String, ASTNode> getDomains() { return domains; }

    public HashMap<String, categoryentry> getCategories() { return category; }

    public categoryentry getCategoryFirst() { return category_first; }

    public int getCategory(String varid) {
        if (category.get(varid) == null) {
            return ASTNode.Undeclared;
        }
        int i = category.get(varid).cat;
        if (i == ASTNode.Auxiliary) {
            return ASTNode.Decision;
        }
        if (i == ASTNode.ConstantMatrix) {
            return ASTNode.Constant;
        }
        if (i == ASTNode.Dim) {
            return ASTNode.Decision;
        }        // not strictly speaking true, but will be filled with decision vars.
        return i;
    }

    public boolean isRelational(String varid) {        // is it a boolean variable or a matrix of boolean vars.
        Domain d = (Domain) domains.get(varid);        // If domain was an identifier, should have been substituted by now.
        if (d instanceof BooleanDomain) {
            return true;
        }
        if (d instanceof MatrixDomain) {
            ASTNode base = d.getChildren().get(0);
            if (base instanceof BooleanDomain) {
                return true;
            }
        }
        return false;
    }

    public boolean hasVariable(String varid) {
        return category.containsKey(varid);
    }
    
    /*
    Create a new auxiliary variable and return an Identifier for it.
    */
    public String newAuxId() {
        String newname = "aux" + auxvarcounter;
        while (category.containsKey(newname)) {
            auxvarcounter++;
            newname = "aux" + auxvarcounter;
        }
        auxvarcounter++;
        return newname;
    }
    
    public Identifier newAuxiliaryVariable(long lb, long ub) {
        String newname = newAuxId();
        if (lb != 0 || ub != 1) {
            domains.put(newname, new IntegerDomain(new Range(new NumberConstant(lb), new NumberConstant(ub))));
        } else {
            domains.put(newname, new BooleanDomain());
        }
        
        category_put_end(newname, ASTNode.Auxiliary);
        return new Identifier(newname, this);
    }
    
    public Identifier newAuxiliaryVariable(ASTNode dom) {
        String newname = newAuxId();
        domains.put(newname, dom.copy());
        category_put_end(newname, ASTNode.Auxiliary);
        return new Identifier(newname, this);
    }
    
    // newAuxHelper just takes an expression and makes an auxiliary variable for it.
    // Deals with FilteredDomainStorage. 
    public ASTNode newAuxHelper(ASTNode exp) {
        Intpair bnd=exp.getBounds();
        ASTNode auxdom=m.filt.constructDomain(exp, bnd.lower, bnd.upper);  //  Look up stored (filtered) domain if there is one.
        
        ASTNode auxvar=newAuxiliaryVariable(auxdom);
        m.filt.auxVarRepresentsAST(auxvar.toString(), exp);    //  Associate the expression to the variable in FilteredDomainStorage
        return auxvar;
    }
    
    //  Used exclusively by class-level flattening.
    public Identifier newAuxiliaryVariable(ASTNode lb, ASTNode ub) {
        String newname = newAuxId();
        domains.put(newname, new IntegerDomain(new Range(lb, ub)));
        category_put_end(newname, ASTNode.Auxiliary);
        return new Identifier(newname, this);
    }
    
    //  Create matrix of aux variables. Also used exclusively by class-level flattening.
    public Identifier newAuxiliaryVariableMatrix(ASTNode lb, ASTNode ub, ArrayList<ASTNode> q_id, ArrayList<ASTNode> qdoms, ArrayList<ASTNode> conditions) {
        // Class-level flattening requires sausage matrix.
        String newname = newAuxId();
        category_put_end(newname, ASTNode.Auxiliary);

        // indexed by the quantifier domains.
        domains.put(newname, new MatrixDomain( new IntegerDomain(new Range(lb, ub)), qdoms, new Container(q_id), new And(conditions)));

        return new Identifier(newname, this);
    }
    
    // Add some info that is helpful for debugging
    public void auxVarRepresentsConstraint(String name, String ct) {
        represents_ct.put(name, ct);
    }

    public String toString() {
        StringBuffer b = new StringBuffer();
        categoryentry c = category_first;
        while (c != null) {
            String name = c.name;
            ASTNode dom = getDomain(name);
            
            if (getCategory(name) == ASTNode.Parameter) {
                b.append("given ");
            } else if (getCategory(name) == ASTNode.Decision) {
                b.append("find ");
            }
            
            b.append(name);
            b.append(" : ");
            b.append(dom.toString());
            b.append("\n");
            c = c.next;
        }
        return b.toString();
    }

    public void simplify() {
        // Simplify the expressions in the domains and matrices of constants.
        TransformSimplify ts = new TransformSimplify();
        Iterator<Map.Entry<String, ASTNode>> itr = domains.entrySet().iterator();

        ArrayList<String> delete_vars = new ArrayList<String>();

        // Simplify lettings_givens.
        int size = lettings_givens.size();
        for (int i =0; i < size; i++) {            // For each one, take it off the front and add back to the end of the deque.
            lettings_givens.addLast(ts.transform(lettings_givens.removeFirst()));
        }
        
        while (itr.hasNext()) {
            Map.Entry<String, ASTNode> d = itr.next();

            ASTNode dom = d.getValue();
            // atl.transform(dom);
            dom = ts.transform(dom);
            d.setValue(dom);

            // Check for unit domains.  Sometimes arise after unifying two vars,
            // or might be given by the user.
            // LEAVE EMPTY DOMAINS ALONE.

            if (CmdFlags.getUseDeleteVars()) {
                if (dom.getCategory() == ASTNode.Constant && dom.isFiniteSet()) {
                    Intpair bnds = dom.getBounds();
                    if (bnds.lower==bnds.upper) {
                        delete_vars.add(d.getKey());
                    }
                }
            }
        }
        
        // Now delete the unit variables.
        if (CmdFlags.getUseDeleteVars() && (!CmdFlags.getUseAggregate() || CmdFlags.getAfterAggregate())) {
            for (int i =0; i < delete_vars.size(); i++) {
                ASTNode value = domains.get(delete_vars.get(i)).getBoundsAST().e1.copy();  // Bool or Int issue?
                assignVariable(new Identifier(delete_vars.get(i), this), value);
            }
        }

        Iterator<Map.Entry<String, ASTNode>> itr2 = constant_matrices.entrySet().iterator();
        while (itr2.hasNext()) {
            Map.Entry<String, ASTNode> d = itr2.next();
            ASTNode tmp = d.getValue();
            tmp = ts.transform(tmp);
            d.setValue(tmp);
        }

    }

    public void transform_all(TreeTransformer t) {
        // Poke into every corner and apply t.

        // do lettings_givens
        int size = lettings_givens.size();
        for (int i =0; i < size; i++) {            // For each one, take it off the front and add back to the end of the deque.
            lettings_givens.addLast(t.transform(lettings_givens.removeFirst()));
        }

        // Domains
        Iterator<Map.Entry<String, ASTNode>> itr = domains.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, ASTNode> d = itr.next();
            ASTNode dom = d.getValue();
            d.setValue(t.transform(dom));
        }

        itr = constant_matrices.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, ASTNode> d = itr.next();
            ASTNode mat = d.getValue();
            d.setValue(t.transform(mat));
        }

        itr = deleted_matrices.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, ASTNode> d = itr.next();
            ASTNode mat = d.getValue();
            d.setValue(t.transform(mat));
        }

        itr = matrix_original_domain.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, ASTNode> d = itr.next();
            ASTNode dom = d.getValue();
            d.setValue(t.transform(dom));
        }

        HashMap<ASTNode, ASTNode> newreplacements = new HashMap<ASTNode, ASTNode>();
        Iterator<Map.Entry<ASTNode, ASTNode>> itr2 = replacements.entrySet().iterator();
        while (itr2.hasNext()) {
            Map.Entry<ASTNode, ASTNode> d = itr2.next();
            ASTNode left = t.transform(d.getKey());
            ASTNode right = t.transform(d.getValue());
            newreplacements.put(left, right);
        }
        this.replacements = newreplacements;

        HashMap<ASTNode, ASTNode> newreplacements_domains = new HashMap<ASTNode, ASTNode>();
        itr2 = replacements_domains.entrySet().iterator();
        while (itr2.hasNext()) {
            Map.Entry<ASTNode, ASTNode> d = itr2.next();
            ASTNode left = t.transform(d.getKey());
            ASTNode right = t.transform(d.getValue());
            newreplacements_domains.put(left, right);
        }
        this.replacements_domains = newreplacements_domains;

        HashMap<ASTNode, Integer> newreplacements_category = new HashMap<ASTNode, Integer>();
        Iterator<Map.Entry<ASTNode, Integer>> itr3 = replacements_category.entrySet().iterator();
        while (itr3.hasNext()) {
            Map.Entry<ASTNode, Integer> d = itr3.next();
            ASTNode left = t.transform(d.getKey());
            newreplacements_category.put(left, (int) d.getValue());
        }
        this.replacements_category = newreplacements_category;
    }

    public void substitute(ASTNode toreplace, ASTNode replacement) {
        ReplaceASTNode t = new ReplaceASTNode(toreplace, replacement);

        Iterator<Map.Entry<String, ASTNode>> itr = domains.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, ASTNode> d = itr.next();
            ASTNode dom = d.getValue();

            d.setValue(t.transform(dom));
        }

        Iterator<Map.Entry<String, ASTNode>> itr2 = constant_matrices.entrySet().iterator();
        while (itr2.hasNext()) {
            Map.Entry<String, ASTNode> d = itr2.next();

            ASTNode mat = d.getValue();

            d.setValue(t.transform(mat));
        }

        int size = lettings_givens.size();
        for (int i =0; i < size; i++) {            // For each one, take it off the front and add back to the end of the deque.
            ASTNode letgiv = lettings_givens.removeFirst();
            ASTNode firstchild = letgiv.getChild(0);            // Don't sub into the first child-- it's the identifier.
            letgiv = t.transform(letgiv);
            letgiv.setChild(0, firstchild);
            lettings_givens.addLast(letgiv);
        }
    }

    public boolean typecheck() {
        // At this point, lettings have been substituted in so all things in
        // 'domains' should be of type Domain.
        for (String a : domains.keySet()) {
            ASTNode d = domains.get(a);
            if (! (d instanceof Domain)) {
                CmdFlags.println("ERROR: Found " + d + " when expecting domain for symbol " + a);
                return false;
            }
            if (!d.typecheck(this)) {
                return false;
            }

            if (getCategory(a) == ASTNode.Decision) {
                // Do some extra checks for finiteness for decision variable matrices.
                if (d instanceof MatrixDomain) {
                    for (int i =3; i < d.numChildren(); i++) {
                        if(!(d.getChild(i) instanceof MatrixDomain) && !(d.getChild(i).isFiniteSet())) {
                            CmdFlags.println("ERROR: Found " + d.getChild(i) + " when expecting finite integer domain for indices of matrix variable " + a);
                            return false;
                        }
                    }
                    if (!(d.getChild(0).isFiniteSet())) {
                        CmdFlags.println("ERROR: Found " + d.getChild(0) + " when expecting finite integer domain for decision variable " + a);
                        return false;
                    }
                } else if (d instanceof HoleyMatrixDomain) {
                    // Might need to do some checks here..
                } else if (!(d.isFiniteSet())) {
                    CmdFlags.println("ERROR: Found " + d + " when expecting finite integer domain for decision variable " + a);
                    return false;
                }
            }
        }
        if (!checkConstantMatrices()) {
            return false;
        }
        return true;
    }

    // Delete a symbol from the table for good.
    public void deleteSymbol(String name) {
        assert category.containsKey(name);
        categoryentry c = category.get(name);
        if (c.prev != null) {
            c.prev.next = c.next;
        } else {
            category_first = c.next;
        }
        if (c.next != null) {
            c.next.prev = c.prev;
        } else {
            category_last = c.prev;
        }
        category.remove(name);

        if (domains.containsKey(name)) {
            domains.remove(name);
        }
        if (constant_matrices.containsKey(name)) {
            constant_matrices.remove(name);
        }
    }

    public void deleteMatrix(String name) {
        // This symbol is a matrix of decision vars that has been replaced by individual decision vars
        // Delete until parsing.
        assert category.containsKey(name);
        categoryentry c = category.get(name);
        if (c.prev != null) {
            c.prev.next = c.next;
        } else {
            category_first = c.next;
        }
        if (c.next != null) {
            c.next.prev = c.prev;
        } else {
            category_last = c.prev;
        }
        category.remove(name);
        assert domains.containsKey(name);

        deleted_matrices.put(name, domains.get(name));
        domains.remove(name);
    }

    // Level of propagation in Minion when using -reduce-domains?
    public String minionReduceDomainsLevel() {
        String st = "SACBounds";        // default

        int numbounds =0;
        categoryentry itr = category_first;
        while (itr != null) {
            // Not auxiliary
            if (itr.cat == ASTNode.Decision || itr.cat == ASTNode.Auxiliary) {
                ArrayList<Intpair> setintervals = domains.get(itr.name).getIntervalSet();
                if (setintervals.size() > 0) {
                    long rangesize = setintervals.get(setintervals.size() - 1).upper - setintervals.get(0).lower + 1L;
                    if (rangesize > CmdFlags.getBoundVarThreshold()) {
                        if (rangesize > CmdFlags.getBoundVarThreshold() * 5) {
                            // If domain is 'really big' reduce prop level.
                            st = "GAC";
                        } else if (rangesize > CmdFlags.getBoundVarThreshold()) {
                            numbounds++;
                        }
                    }
                }
            }
            itr = itr.next;
        }
        if (numbounds > 5) {
            // If there are 'many' large variables, reduce prop level.
            st = "GAC";
        }
        return st;
    }
    
    
    
    //////////////////////////////////////////////////////////////////////////// 
    // 
    // Output methods

    // Mangling and demangling for serialization.

    private void mangle_before_serialization() {
        category_list = new ArrayList<categoryentry>();

        categoryentry cur = category_first;
        while (cur != null) {
            category_list.add(cur);
            cur = cur.next;
        }

        // unlink the list.
        cur = category_first;
        while (cur != null) {
            cur.prev = null;
            cur = cur.next;
            if (cur != null) {
                cur.prev.next = null;
            }
        }
    }

    public void unmangle_after_serialization() {
        if (category_list.size() > 0) {
            category_first = category_list.get(0);
            category_last = category_list.get(category_list.size() - 1);
        } else {
            category_first = null; category_last = null;
        }

        for (int i =0; i < category_list.size(); i++) {
            if (i > 0) {
                category_list.get(i).prev = category_list.get(i - 1);
            } else {
                category_list.get(i).prev = null;
            }

            if (i < category_list.size() - 1) {
                category_list.get(i).next = category_list.get(i + 1);
            } else {
                category_list.get(i).next = null;
            }
        }
        category_list = null;
    }
    
    protected void serialize() {
        Model tmp = m;
        m = null;        // disconnect from the model so we don't serialise that.
        
        mangle_before_serialization();
        try {
            FileOutputStream sts = new FileOutputStream(CmdFlags.auxfile);
            ObjectOutputStream out = new ObjectOutputStream(sts);
            out.writeObject(this);
            out.close();
            sts.close();
        } catch (Exception e) {
            CmdFlags.println(Thread.currentThread().getStackTrace());
            for (StackTraceElement t : e.getStackTrace()) {
                System.out.println(t);
            }
            CmdFlags.println("WARNING: Failed to serialise: " + e);
        }
        unmangle_after_serialization();
        m = tmp;
    }
    
    public void toMinion(StringBuffer b) {
        assert m.global_symbols == this;
        
        if(!CmdFlags.getRunSolver()) {
            // Serialise the symbol table only if we are not running the back-end solver.
            serialize();
        }
        
        categoryentry itr = category_first;
        while (itr != null) {
            // Not auxiliary
            if (itr.cat == ASTNode.Decision) {
                output_variable(b, itr.name, (Domain) domains.get(itr.name));
            }
            itr = itr.next;
        }
        
        // Now do auxiliaries
        itr = category_first;
        while (itr != null) {
            if (itr.cat == ASTNode.Auxiliary) {
                output_variable(b, itr.name, (Domain) domains.get(itr.name));
            }
            itr = itr.next;
        }
        
        // Output constant matrices.
        itr = category_first;
        while (itr != null) {
            String name = itr.name;
            if (itr.cat == ASTNode.ConstantMatrix) {
                // If two dimensional, print as tuplelist.
                if (((MatrixDomain) domains.get(name)).getMDIndexDomains().size() == 2) {
                    b.append("**TUPLELIST**\n");
                    b.append(name + " ");
                    ASTNode a = constant_matrices.get(name);
                    b.append(a.numChildren() - 1 + " ");
                    b.append(a.getChild(1).numChildren() - 1 + "\n");                    // Obviously doesn't work with empty lists...
                    for (int i =1; i < a.numChildren(); i++) {
                        ASTNode tuple = a.getChild(i);
                        assert tuple instanceof CompoundMatrix;
                        for (int j =1; j < tuple.numChildren(); j++) {
                            ASTNode element = tuple.getChild(j);
                            element.toMinion(b, false);
                            b.append(" ");
                        }
                        b.append("\n");
                    }
                    b.append("**VARIABLES**\n");
                }
                // Print as array of constants, in case there is a matrix deref
                // that is translated to element.
                ArrayList<Long> dimensions = getConstantMatrixSize(constant_matrices.get(name));
                
                // Minion won't accept a matrix with a dimension of size 0. 
                
                boolean zerodimension=false;
                for(int i=0; i<dimensions.size(); i++) {
                    if(dimensions.get(i)==0) zerodimension=true;
                }
                
                if(!zerodimension) {
                    b.append("ALIAS " + name + "[");
                    for (int i =0; i < dimensions.size(); i++) {
                        b.append(dimensions.get(i));
                        if (i < dimensions.size() - 1) {
                            b.append(",");
                        }
                    }
                    
                    b.append("]=");
                    constant_matrices.get(name).toMinion(b, false);
                    b.append("\n");
                }
                
            }
            itr = itr.next;
        }
    }

    public void printPrintStmt(StringBuffer b) {
        b.append("PRINT[");
        String sep = "";
        categoryentry itr = category_first;
        while (itr != null) {
            String name = itr.name;
            
            if (itr.cat == ASTNode.Decision) {                // Not auxiliary
                b.append(sep);
                if (getDomain(name) instanceof SimpleDomain) {
                    b.append("[");
                    b.append(name);
                    b.append("]");
                } else {
                    b.append(name);
                }
                sep = ",";
            }
            itr = itr.next;
        }
        // Objective -- last.
        if(m.objective!=null) {
            b.append("[");
            b.append(m.objective.getChild(0).toString());
            b.append("]");
        }
        
        b.append("]\n");
    }

    public void printAllVariables(StringBuffer b, int cat) {
        String sep = "";
        categoryentry itr = category_first;
        while (itr != null) {
            String name = itr.name;            // (String) d.getKey();

            if (itr.cat == cat) {
                b.append(sep);
                b.append(name);
                sep = ",";
            }
            itr = itr.next;
        }

    }
    
    public boolean printAllVariablesFlatzinc(StringBuffer b, int cat) {
        String sep = "";
        categoryentry itr = category_first;
        boolean hasVariables=false;
        while (itr != null) {
            String name = itr.name;
            
            if (itr.cat == cat) {
                b.append(sep);
                
                if (getDomain(name).equals(new BooleanDomain()) || getDomain(name).equals(new IntegerDomain(new Range(new NumberConstant(0), new NumberConstant(1))))) {
                    if (boolvar_int.containsKey(name)) {
                        b.append(name + "_INTEGER");
                    } else if (boolvar_bool.containsKey(name)) {
                        b.append(name + "_BOOL");
                    } else {
                        assert false : "Something strange has happened: var with name " + name + " is apparently not used anywhere.";
                    }
                } else {
                    b.append(name);
                }
                hasVariables=true;
                sep = ",";
            }
            itr = itr.next;
        }
        return hasVariables;
    }
    
    // Output variable declarations.
    private void output_variable(StringBuffer b, String name, Domain dom) {
        String ct = represents_ct.get(name);
        if (ct == null) {
            ct = "";
        }
        if (dom instanceof BooleanDomain && dom.containsValue(0) && dom.containsValue(1)) {
            b.append("BOOL " + name + " #" + ct + "\n");
        } else if (dom instanceof SimpleDomain || dom instanceof BooleanDomain) {
            ArrayList<Intpair> setintervals = dom.getIntervalSet();
            if (setintervals.size() > 0) {
                long rangesize = setintervals.get(setintervals.size() - 1).upper - setintervals.get(0).lower + 1L;

                if (CmdFlags.getUseBoundVars() && rangesize > CmdFlags.getBoundVarThreshold()) {
                    b.append("BOUND " + name + " #" + ct + "\n");
                } else {
                    b.append("DISCRETE " + name + " #" + ct + "\n");
                }

                b.append("{" + setintervals.get(0).lower + ".." + setintervals.get(setintervals.size() - 1).upper + "}\n");
                if (setintervals.size() > 1) {                    // It's not a complete range; need to knock out some vals.
                    b.append("**CONSTRAINTS**\n");
                    b.append("w-inintervalset(" + name + ", [");
                    for (int i =0; i < setintervals.size(); i++) {
                        b.append(setintervals.get(i).lower);
                        b.append(",");
                        b.append(setintervals.get(i).upper);
                        if (i < setintervals.size() - 1) {
                            b.append(",");
                        }
                    }
                    b.append("])\n");
                    b.append("**VARIABLES**\n");
                }
            } else {
                // Empty domain
                b.append("DISCRETE " + name + " #" + ct + "\n");
                b.append("{0..0}  #  This is an empty domain. Faking that by using 0..0 and the false() constraint below.\n");
                b.append("**CONSTRAINTS**\n");
                b.append("false()\n");
                b.append("**VARIABLES**\n");
            }
        } else {
            assert false;
        }

    }

    //////////////////////////////////////////////////////////////////////////// 
    // 
    // Dominion output

    public void toDominion(StringBuffer b) {
        TransformSimplify ts = new TransformSimplify();
        categoryentry itr = category_first;
        while (itr != null) {
            String name = itr.name;
            Domain dom = (Domain) getDomain(name);

            if (itr.cat == ASTNode.Parameter) {
                b.append("given " + name);
                if (dom instanceof MatrixDomain) {
                    MatrixDomain md = (MatrixDomain) dom;
                    b.append("[");
                    for (int i =3; i < md.numChildren(); i++) {
                        assert md.getChild(i).getBoundsAST().e1.equals(new NumberConstant(0));
                        ASTNode upperbound = BinOp.makeBinOp("+", md.getChild(i).getBoundsAST().e2, new NumberConstant(1));
                        upperbound = ts.transform(upperbound);
                        upperbound.toDominionParam(b);
                        if (i < md.numChildren() - 1) {
                            b.append(",");
                        }
                    }
                    b.append("]");
                    dom = (Domain) md.getChild(0);
                }
                b.append(": int {");
                dom.toDominionParam(b);
                b.append("}\n");
            } else if (itr.cat == ASTNode.Decision || itr.cat == ASTNode.Auxiliary) {
                output_variable_dominion(b, name, dom);
            } else if (itr.cat == ASTNode.ConstantMatrix) {
                ArrayList<Long> dimensions = getConstantMatrixSize(constant_matrices.get(name));

                b.append("define " + name + "[");

                for (int i =0; i < dimensions.size(); i++) {
                    b.append("..");
                    if (i < dimensions.size() - 1) {
                        b.append(",");
                    }
                }

                b.append("] = ");

                StringBuffer buff = new StringBuffer();
                constant_matrices.get(name).toDominionParam(buff);
                b.append(buff.toString() + "\n");
            } else {
                System.out.println("WARNING: Found something in symbol table that cannot be output to Dominion: " + name);
            }

            itr = itr.next;
        }
    }

    // Output variable declarations, including matrices with conditions.
    // Switched this method over to toDominionParam.
    private void output_variable_dominion(StringBuffer b, String name, Domain dom) {
        String ct = represents_ct.get(name);
        if (ct == null) {
            ct = "";
        }
        if (dom instanceof BooleanDomain) {
            b.append("find " + name + " : bool $" + ct + "\n");
        } else if (dom instanceof SimpleDomain) {
            b.append("find " + name + " : int {");
            dom.toDominionParam(b);

            b.append("} $" + ct + "\n");
        } else if (dom instanceof MatrixDomain) {
            // Dim statement
            b.append("dim " + name + "[");
            ArrayList<ASTNode> doms = dom.getChildren();
            ASTNode basedom = doms.get(0);

            ArrayList<ASTNode> indices = new ArrayList<ASTNode>(doms.subList(3, doms.size()));
            ArrayList<ASTNode> dim_id = dom.getChild(1).getChildren();            // get contents of IdList
            ArrayList<ASTNode> conditions = new ArrayList<ASTNode>();
            if (dom.getChild(2) instanceof And) {
                conditions.addAll(dom.getChild(2).getChildren());
            } else if (!dom.getChild(2).equals(new BooleanConstant(true))) {
                conditions.add(dom.getChild(2));
            }

            assert indices.size() == dim_id.size() || dim_id.size() == 0;

            // List of dimension sizes
            for (int i =0; i < indices.size(); i++) {
                assert indices.get(i).getBoundsAST().e1.equals(new NumberConstant(0));
                ASTNode upperbound = BinOp.makeBinOp("+", indices.get(i).getBoundsAST().e2, new NumberConstant(1));
                TransformSimplify ts = new TransformSimplify();
                upperbound = ts.transform(upperbound);
                upperbound.toDominionParam(b);
                if (i < indices.size() - 1) {
                    b.append(",");
                }
            }
            b.append("] : int\n");

            if (dim_id.size() == 0) {
                // find statement for whole matrix.
                b.append("find " + name + "[");
                for (int i =0; i < indices.size(); i++) {
                    b.append("..");
                    if (i < indices.size() - 1) {
                        b.append(",");
                    }
                }
                b.append("] : int {");

                basedom.toDominionParam(b);
                b.append("}\n");

            } else {
                // Find comprehension.
                b.append("[ find " + name + "[");

                for (int i =0; i < dim_id.size(); i++) {
                    dim_id.get(i).toDominionParam(b);
                    if (i < dim_id.size() - 1) {
                        b.append(",");
                    }
                }

                b.append("]: int {");
                basedom.toDominionParam(b);
                b.append("} | ");

                // Now print the domains of each dim_id
                for (int i =0; i < dim_id.size(); i++) {
                    dim_id.get(i).toDominionParam(b);
                    b.append(" in {");
                    indices.get(i).toDominionParam(b);
                    b.append("}");
                    if (i < dim_id.size() - 1) {
                        b.append(", ");
                    }
                }

                // Now the conditions.
                if (conditions.size() > 0) {
                    b.append(", ");
                }
                for (int i =0; i < conditions.size(); i++) {
                    conditions.get(i).toDominionParam(b);
                    if (i < conditions.size() - 1) {
                        b.append(", ");
                    }
                }

                b.append("]\n");
            }
            if (! ct.equals("")) {
                b.append("$ representing constraint: " + ct + "\n");
            }
        } else {
            assert false;
        }

    }

    //////////////////////////////////////////////////////////////////////////// 
    // 
    // Flatzinc output for Gecode

    // Special methods for marking BooleanDomain variables as bool or int or both.
    
    public void markAsBoolGecode(String name) {
        boolvar_bool.put(name, true);
    }

    public void markAsIntGecode(String name) {
        boolvar_int.put(name, true);
    }

    public void toFlatzinc(StringBuffer b) {
        StringBuffer constraints = new StringBuffer();
        categoryentry itr = category_first;
        while (itr != null) {
            String name = itr.name;
            Domain dom = (Domain) getDomain(name);

            // Not auxiliary
            if (itr.cat == ASTNode.Decision) {
                output_variable_flatzinc(b, name, dom, constraints);
            }
            itr = itr.next;
        }

        // Now do auxiliaries
        itr = category_first;
        while (itr != null) {
            String name = itr.name;
            Domain dom = (Domain) getDomain(name);

            // Not auxiliary
            if (itr.cat == ASTNode.Auxiliary) {
                output_variable_flatzinc(b, name, dom, constraints);
            }
            itr = itr.next;
        }

        // Constant matrices
        // look like this: array [1..10] of int: b =  [0, 0, 0, 0, 0, 0, 0, 0, 0, -50];
        itr = category_first;
        while (itr != null) {
            String name = itr.name;

            if (itr.cat == ASTNode.ConstantMatrix) {
                ASTNode mat = constant_matrices.get(itr.name);
                ArrayList<Long> dim = getConstantMatrixSize(mat);
                ArrayList<Long> cont = getConstantMatrixContents(mat);
                if (dim.size() == 1) {
                    b.append("array [1.." + dim.get(0) + "] of int: " + itr.name + " = " + cont.toString() + ";\n");
                }
            }
            itr = itr.next;
        }

        // Now dump the new constraints into b.
        b.append(constraints);

        // bool2int constraints
        itr = category_first;
        while (itr != null) {
            String name = itr.name;
            Domain dom = (Domain) getDomain(name);

            if((itr.cat == ASTNode.Decision || itr.cat == ASTNode.Auxiliary) && (dom instanceof BooleanDomain || dom.equals(new IntegerDomain(new Range(new NumberConstant(0), new NumberConstant(1))))) 
                && boolvar_bool.containsKey(name) && boolvar_int.containsKey(name)) {
                b.append("constraint bool2int(" + name + "_BOOL," + name + "_INTEGER);\n");
            }
            itr = itr.next;
        }
    }
    
    // Output variable declarations.
    private void output_variable_flatzinc(StringBuffer b, String name, Domain dom, StringBuffer c) {
        // b is for variables, c is for constraints.
        String ct = represents_ct.get(name);
        if (ct == null) {
            ct = "";
        }
        if (dom instanceof BooleanDomain || dom.equals(new IntegerDomain(new Range(new NumberConstant(0), new NumberConstant(1))))) {
            if (boolvar_bool.containsKey(name)) {
                b.append("var bool: " + name + "_BOOL");
                // i.e. not auxiliary and not also present as an int.
                if (category.get(name).cat == ASTNode.Decision && !boolvar_int.containsKey(name)) {
                    b.append("::output_var");
                }
                b.append("; %" + ct + "\n");
            }
            if (boolvar_int.containsKey(name)) {
                b.append("var {0,1}: " + name + "_INTEGER ");
                // i.e. not auxiliary
                if (category.get(name).cat == ASTNode.Decision) {
                    b.append("::output_var");
                }
                b.append("; %" + ct + "\n");
            }
        } else if (dom instanceof SimpleDomain) {
            ArrayList<Intpair> set = dom.getIntervalSet();
            if(set.size()==0) {
                b.append("var 0..0 : " + name);
                if (category.get(name).cat == ASTNode.Decision) {
                    b.append("::output_var");
                }
                b.append("; % Empty domain simulated with 0..0 and bool_eq(true,false). " + ct + "\n");
                c.append("constraint bool_eq(true,false);\n");  // Empty domain. 
            }
            else {
                b.append("var " + set.get(0).lower + ".." + set.get(set.size()-1).upper + ": " + name);
                // i.e. not auxiliary
                if (category.get(name).cat == ASTNode.Decision) {
                    b.append("::output_var");
                }
                b.append("; %" + ct + "\n");
    
                if (set.size() > 1) {                // It's not a complete range
                    c.append("constraint set_in(" + name + ",{");
                    for (int i =0; i < set.size(); i++) {
                        for (long j = set.get(i).lower; j <= set.get(i).upper; j++) {
                            c.append(j);
                            if (i < set.size() - 1 || j < set.get(i).upper) {
                                c.append(",");
                            }
                        }
                    }

                    c.append("});\n");
                }
            }
        } else {
            assert false;
        }

    }

    //////////////////////////////////////////////////////////////////////////// 
    // Minizinc

    public void toMinizinc(StringBuffer b) {
        if(!CmdFlags.getRunSolver()) {
            // Serialise the symbol table only if we are not running the back-end solver.
            serialize();
        }
        
        categoryentry itr = category_first;
        while (itr != null) {
            String name = itr.name;
            Domain dom = (Domain) getDomain(name);

            // Not auxiliary
            if (itr.cat == ASTNode.Decision) {
                output_variable_minizinc(b, name, dom);
            }
            itr = itr.next;
        }

        // Now do auxiliaries
        itr = category_first;
        while (itr != null) {
            String name = itr.name;
            Domain dom = (Domain) getDomain(name);

            // Not auxiliary
            if (itr.cat == ASTNode.Auxiliary) {
                output_variable_minizinc(b, name, dom);
            }
            itr = itr.next;
        }

        // Constant matrices
        // look like this: array [1..10] of int: b =  [0, 0, 0, 0, 0, 0, 0, 0, 0, -50];
        itr = category_first;
        while (itr != null) {
            String name = itr.name;

            if (itr.cat == ASTNode.ConstantMatrix) {
                ASTNode mat = constant_matrices.get(itr.name);
                ArrayList<Long> dim = getConstantMatrixSize(mat);
                ArrayList<Long> cont = getConstantMatrixContents(mat);
                if (dim.size() == 1) {
                    b.append("array [1.." + dim.get(0) + "] of int: " + itr.name + " = " + cont.toString() + ";\n");
                }
            }
            itr = itr.next;
        }

        // Link boolean and integer variables.
        itr = category_first;
        while (itr != null) {
            String name = itr.name;
            Domain dom = (Domain) getDomain(name);

            if ((itr.cat == ASTNode.Decision || itr.cat == ASTNode.Auxiliary) && (dom instanceof BooleanDomain || dom.equals(new IntegerDomain(new Range(new NumberConstant(0), new NumberConstant(1))))) 
                && boolvar_bool.containsKey(name) && boolvar_int.containsKey(name)) {
                b.append("constraint bool2int(" + name + "_BOOL) = " + name + "_INTEGER;\n");
            }
            itr = itr.next;
        }
    }
    
    // Output variable declarations -- minizinc
    private void output_variable_minizinc(StringBuffer b, String name, Domain dom) {
        String ct = represents_ct.get(name);
        if (ct == null) {
            ct = "";
        }
        if(dom instanceof BooleanDomain || getDomain(name).equals(new IntegerDomain(new Range(new NumberConstant(0), new NumberConstant(1))))) {
            if (boolvar_bool.containsKey(name)) {
                b.append("var bool: " + name + "_BOOL; %" + ct + "\n");
            }
            if (boolvar_int.containsKey(name)) {
                b.append("var {0,1}: " + name + "_INTEGER; %" + ct + "\n");
            }
        }
        else if(dom instanceof SimpleDomain) {
            ArrayList<Intpair> set = dom.getIntervalSet();
            if (set.size() > 1) {                // It's not a complete range:
                // Output something like this:
                // set of int: aux1765 = 2..5 union 10..20;
                // var aux1765 : x;
                String setname = newAuxId();
                b.append("set of int : " + setname + " = ");
                for (int i =0; i < set.size(); i++) {
                    b.append(set.get(i).lower + ".." + set.get(i).upper);
                    if (i < set.size() - 1) {
                        b.append(" union ");
                    }
                }
                
                b.append(";\n");
                b.append("var " + setname + " : " + name);

            } else {
                b.append("var " + set.get(0).lower + ".." + set.get(0).upper + ": " + name);
            }
            b.append("; %" + ct + "\n");
        }
        else {
            assert false;
        }
    }
    
    public void showVariablesMinizinc(StringBuffer b) {
        categoryentry itr = category_first;
        boolean trailingcomma = false;
        while (itr != null) {
            String name = itr.name;
            Domain dom = (Domain) getDomain(name);

            // Not auxiliary
            if (itr.cat == ASTNode.Decision) {
                b.append("show(");

                if (dom instanceof BooleanDomain || dom.equals(new IntegerDomain(new Range(new NumberConstant(0), new NumberConstant(1))))) {
                    if (boolvar_bool.containsKey(name)) {
                        b.append(name + "_BOOL");
                    } else if (boolvar_int.containsKey(name)) {
                        b.append(name + "_INTEGER");
                    }
                } else {
                    b.append(name);
                }

                b.append("),\" \",");
                trailingcomma = true;
            }
            itr = itr.next;
        }

        // take off trailing comma.
        if (trailingcomma) {
            b.deleteCharAt(b.length() - 1);
        }
    }
    
    ////////////////////////////////////////////////////////////////////////////
    // Data and functions for SAT output.
    
    public void markAsDirectSAT(String name) {
        if(directvar_sat==null) directvar_sat=new HashSet<String>();
        directvar_sat.add(name);
    }
    
    public boolean isDirectSAT(String name) {
        if(directvar_sat==null) {
            return false;
        }
        else {
            return directvar_sat.contains(name);
        }
    }
    
    //////////////////////////////////////////////////////////////////////////// 
    // JSON output of model

    public void writeVarDomainsAsJSON(StringBuffer bf) {
        bf.append("[");
        categoryentry c = category_first;
        boolean decisionFound = false;
        while (c != null) {
            if (c.cat == ASTNode.Decision) {
                if (decisionFound) {
                    bf.append(",");
                } else {
                    decisionFound = true;
                }
                bf.append("\n");
                ASTNode domain = getDomain(c.name);
                bf.append("{\n\"name\": \"" + escapeVar(c.name) + "\",\n");
                bf.append("\"domain\": [");
                ArrayList<Intpair> domPairs = domain.getIntervalSet();
                for (int i = 0; i < domPairs.size(); i++) {
                    bf.append("[" + domPairs.get(i).lower + ", " + domPairs.get(i).upper + "]");
                    if (i < domPairs.size() - 1) {
                        bf.append(",");
                    }
                }
                bf.append("]\n}");
            }

            c = c.next;
        }
        bf.append("]");
    }

    public void writeVarListAsJSON(StringBuffer bf) {
        bf.append("[");
        categoryentry c = category_first;
        boolean decisionFound = false;
        while (c != null) {
            if (c.cat == ASTNode.Decision) {
                if (decisionFound) {
                    bf.append(",");
                } else {
                    decisionFound = true;
                }
                bf.append("\"" + escapeVar(c.name) + "\"");
            }
            c = c.next;
        }
        bf.append("]");
    }

    String escapeVar(String s) {
        return "$" + s;
    }

    
    public static String unescapeVar(String s) {
        return s.replaceAll("^\\$(.*)$", "$1");
    }
    
    public ArrayList<String>  getVarNamesList() {
        ArrayList<String> vars = new ArrayList<String>();
        categoryentry c = category_first;
        boolean decisionFound = false;
        while (c != null) {
            if (c.cat == ASTNode.Decision) {
                vars.add(c.name);
            }
            c = c.next;
        }
        return vars;
    }
}
