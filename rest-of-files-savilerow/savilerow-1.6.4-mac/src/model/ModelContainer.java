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

import savilerow.expression.*;
import savilerow.treetransformer.*;
import savilerow.eprimeparser.EPrimeReader;
import savilerow.model.*;
import savilerow.solver.*;
import savilerow.*;

import java.util.*;
import java.io.*;

// This class controls the refinement process.

public class ModelContainer {
    
    public Model m;
    public ArrayList<ASTNode> parameters;
    
    public ModelContainer(Model _m, ArrayList<ASTNode> _parameters) {
        m = _m;
        parameters = _parameters;
    }
    
    public void process() {
        processPreamble();
        
        if (CmdFlags.getClasstrans()) {
            classLevelFlattening();
        } else {
            instancePreFlattening1();
            
            if (CmdFlags.getUsePropagate()) {
                squashDomains();
                CmdFlags.currentModel=m;
            }
            
            instancePreFlattening2(false);
            
            if (CmdFlags.getMode() == CmdFlags.Multi) {
                branchingInstanceFlattening();
            } else {
                instanceFlattening(-1, false);                // no model number.
                postFlattening(-1, false);
            }
        }
    }
    
    // Same as process except does not run minion at the end.
    // This is to get the JVM up to speed.
    public void dryrun() {
        processPreamble();
        
        if (CmdFlags.getClasstrans()) {
            classLevelFlattening();
        } else {
            instancePreFlattening1();
            
            if (CmdFlags.getUsePropagate()) {
                squashDomains();
                CmdFlags.currentModel=m;
            }
            
            instancePreFlattening2(false);
            
            if (CmdFlags.getMode() == CmdFlags.Multi) {
                branchingInstanceFlattening();
            } else {
                instanceFlattening(-1, false);                // no model number.
                // postFlattening(-1, false);
            }
        }
    }
    
    public void processPreamble() {
        CmdFlags.setOutputReady(false);        // Make sure we are not in 'output ready' state.
        CmdFlags.setAfterAggregate(false);
        ////////////////////////////////////////////////////////////////////////////
        // Substitute in the parameters
        
        // Process lettings, givens, wheres, and finds in order of declaration.
        ArrayDeque<ASTNode> preamble = m.global_symbols.lettings_givens;

        // The next loop will pull things out of the parameter file, so first
        // deal with the parameters -- make undef safe, simplify.
        for (int i =0; i < parameters.size(); i++) {
            ASTNode a = parameters.get(i);

            if (!a.typecheck(m.global_symbols)) {
                CmdFlags.println("ERROR: Failed type checking in parameter file:" + a);
                CmdFlags.exit();
            }

            TransformMakeSafe tms = new TransformMakeSafe(m);
            a = tms.transform(a);

            // Extract any extra constraints that were generated and add them to the end of the
            // preamble in a Where statement.
            ASTNode extra_cts = tms.getContextCts();
            if (extra_cts != null) {
                preamble.addLast(new Where(extra_cts));                /// What if the parameter is not used?
            }

            TransformSimplify ts = new TransformSimplify();
            a = ts.transform(a);

            // Unroll and evaluate any matrix comprehensions or quantifiers in the parameters.
            TransformQuantifiedExpression t2 = new TransformQuantifiedExpression(m);
            a = t2.transform(a);

            a = ts.transform(a);

            fixIndexDomainsLetting(a);            // Repair any inconsistency between the indices in a matrix and its matrix domain (if there is one).

            parameters.set(i, a);

            // Scan forward in the parameters to sub this parameter into future ones.
            ReplaceASTNode rep = new ReplaceASTNode(a.getChild(0), a.getChild(1));
            for (int j = i + 1; j < parameters.size(); j++) {
                parameters.set(j, rep.transform(parameters.get(j)));
            }
        }

        // Now go through the preamble in order.
        while (preamble.size() != 0) {
            ASTNode a = preamble.removeFirst();
            
            // Type check, deal with partial functions, simplify
            if (!a.typecheck(m.global_symbols)) {
                CmdFlags.println("ERROR: Failed type checking:" + a);
                CmdFlags.exit();
            }

            TransformMakeSafe tms = new TransformMakeSafe(m);
            a = tms.transform(a);
            // Extract any extra constraints generated in TransformMakeSafe.
            ASTNode extra_cts = tms.getContextCts();
            if (extra_cts != null) {
                preamble.addLast(new Where(extra_cts));
            }

            TransformSimplify ts = new TransformSimplify();
            a = ts.transform(a);
            
            // Process any quantifiers before simplifying again.
            if (!CmdFlags.getClasstrans()) {
                TransformQuantifiedExpression t2 = new TransformQuantifiedExpression(m);
                a = t2.transform(a);
            }
            a = ts.transform(a);

            if (a instanceof Letting) {
                fixIndexDomainsLetting(a);                // Repair any inconsistency between the indices in a matrix and its matrix domain (if there is one).
                processLetting(a);
            } else if (a instanceof Find) {
                processFind(a);
            } else if (a instanceof Where) {
                processWhere(a);
            } else if (a instanceof Given) {
                processGiven(a);
            } else if (a instanceof Dim) {
                processDim(a);
            } else {
                assert a instanceof ForallExpression;
                processForallFind(a);
            }
        }

        if (parameters.size() > 0) {
            CmdFlags.println("WARNING: Number of givens in model file does not match number of lettings in parameter file.");
        }

        parameters = null;        // No longer needed.

        // Run type-checker before any transformation.
        if (!m.typecheck()) {
            CmdFlags.println("ERROR: Failed type checking after substituting in lettings.");
            CmdFlags.exit();
        }
        if(CmdFlags.getVerbose()) {
            CmdFlags.println("Model before undef handling:");
            CmdFlags.println(m);
        }
        
        //  Remove matrices indexed by matrices. 
        removeMatrixIndexedMatrices();
        
        // Deal with partial functions in the constraints/objective/branchingOn
        TransformMakeSafe tms = new TransformMakeSafe(m);
        m.transform(tms);

        // Make sure constant folding is done before the next rule.
        m.simplify();
    }

    ////////////////////////////////////////////////////////////////////////
    //
    // Non-class-level transformations (to Minion/Gecode/Minizinc/SAT)

    public void instancePreFlattening1() {

        HashMap<String, ASTNode> doms = m.global_symbols.getDomains();
        Iterator<Map.Entry<String, ASTNode>> itr = doms.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, ASTNode> a = itr.next();
            if (a.getValue() instanceof MatrixDomain) {

                TransformMatrixIndices ti = new TransformMatrixIndices(0, m, a.getKey());
                m.transform(ti);
            }
        }

        TransformQuantifiedExpression t2 = new TransformQuantifiedExpression(m);
        m.transform(t2);

        ////////////////////////////////////////////////////////////////////////
        // Get rid of bubbles before any type of flattening.
        // Bubbles disrupt flattening/CSE because expressions inside the
        // bubble may have identifiers in them that are not defined outside.
        // Bubbles also mess up TransformMatrixDeref because it can't tell
        // when a matrix is indexed by a local variable.

        TransformToFlatBubble ttfb = new TransformToFlatBubble(m);
        m.transform(ttfb);
        
        ////////////////////////////////////////////////////////////////////////
        //
        // Atomise matrices of variables.
        
        destroyMatrices();
        m.simplify();
        
        // Sub constant matrices into constraints (except for tables into Table).
        TransformSubInConstantMatrices t4 = new TransformSubInConstantMatrices(m);
        m.transform(t4);
        
        ////////////////////////////////////////////////////////////////////////
        //
        // Create element functions from matrix derefs.
        
        TransformMatrixDeref t3 = new TransformMatrixDeref(m);
        m.transform(t3);
        
        ////////////////////////////////////////////////////////////////////////
        // Symmetry detection and breaking
        
        if(CmdFlags.getGraphColSymBreak()) {
            //  Highly specific value symmetry breaking for graph colouring.
            TransformGCAssignClique gc=new TransformGCAssignClique(m);
            m.transform(gc);
        }
        
        if(CmdFlags.getUseVarSymBreaking()) {
            writeModelAsJSON(m);
        }
        
        ////////////////////////////////////////////////////////////////////////
        //
        // If objective function is not a solitary variable, do one flattening op on it.

        if (m.objective != null) {
            ASTNode ob = m.objective.getChild(0);
            if (!ob.isConstant() && ! (ob instanceof Identifier)) {
                boolean flatten = true;
                if (ob instanceof MatrixDeref || ob instanceof SafeMatrixDeref) {
                    flatten = false;
                    for (int i =1; i < ob.numChildren(); i++) {
                        if (! ob.getChild(i).isConstant()) {
                            flatten = true;
                        }
                    }
                }

                if (flatten) {
                    ASTNode auxvar = m.global_symbols.newAuxHelper(ob);
                    ASTNode flatcon = new ToVariable(ob, auxvar);
                    m.global_symbols.auxVarRepresentsConstraint(auxvar.toString(), ob.toString());
                    m.objective.setChild(0, auxvar);
                    m.constraints.setChild(0, new And(flatcon, m.constraints.getChild(0)));
                }
            }
        }
        
        // Put into normal form.
        TransformNormalise tn = new TransformNormalise(m);
        m.transform(tn);
    }

    public void instancePreFlattening2(boolean propagate) {
        ////////////////////////////////////////////////////////////////////////
        // Pre-flattening rearrangement
        
        // Delete redundant variables.
        if (CmdFlags.getRemoveRedundantVars()) {
            RemoveRedundantVars rrv=new RemoveRedundantVars();
            rrv.transform(m);
        }
        
        ////////////////////////////////////////////////////////////////////////
        //
        // Aggregation
        // Just Alldiff and GCC at the moment.
        
        if (CmdFlags.getUseAggregate()) {
            TransformCollectAlldiff tca = new TransformCollectAlldiff(m);
            m.transform(tca);
            
            TransformCollectGCC tcg = new TransformCollectGCC(m);
            m.transform(tcg);
        }
        CmdFlags.setAfterAggregate(true);
        
        if(CmdFlags.getUseDeleteVars() && CmdFlags.getUseAggregate()) {
            m.simplify();  // Delete vars is switched on after aggregation. 
        }
        
        TransformAlldiffExcept tae = new TransformAlldiffExcept(m);
        m.transform(tae);
        
        TransformOccurrence t35 = new TransformOccurrence();
        m.transform(t35);
        
        if (CmdFlags.getUseBoundVars() && CmdFlags.getMiniontrans()) {
            // Weird things to deal with Minion and BOUND variables.
            TransformForBoundVars t36 = new TransformForBoundVars(m);
            m.transform(t36);
        }
        
        // if two sums are equal, rearrange to one sum=0.
        // TransformSumEqualSum tses=new TransformSumEqualSum(m);
        // m.transform(tses);

        // boolvar=1 ==> boolvar,  boolvar=0 ==> not(boolvar).
        TransformBoolEq tbe = new TransformBoolEq(m);
        m.transform(tbe);
        
        ////////////////////////////////////////////////////////////////////////
        //
        // Add pre-flattening implied constraints.
        if(CmdFlags.getUseACCSE() || CmdFlags.getUseACCSEAlt()) {
            //  Add implied sum constraints based on AllDiffs and GCCs. Only when using AC-CSE.
            TransformAlldiffGCCSum tas = new TransformAlldiffGCCSum(m);
            m.transform(tas);
        }
        
        // Add implied sum constraint on card variables based on GCC.
        TransformGCCSum tgs = new TransformGCCSum(m);
        m.transform(tgs);
        
        if(CmdFlags.getUseAggressiveACCSE()) {
            // Turn various types of expression to sums (or create implied cts) to create more AC-CSEs.
            // AtMost, AtLeast
            TransformOccurrenceToSum tots=new TransformOccurrenceToSum();
            m.transform(tots);
            
            // And, Or (Implies)
            TransformLogicToSum tlts=new TransformLogicToSum();
            m.transform(tlts);
            
            // MORE to do here -- equals, less-than, ...
            
        }
        
        ////////////////////////////////////////////////////////////////////////
        // Deal with sum expressions with a comparison < or <= above.
        // 1. Transform sum < X  into sum+1 <= X

        // TransformSumLess t36=new TransformSumLess();
        // m.transform(t36);

        // 2. Transform sum1 <= sum2 into (sum1 - sum2) <= 0
        // so that it goes into one sumleq constraint.

        // TransformSumLeq t37=new TransformSumLeq();
        // m.transform(t37);
        
        ////////////////////////////////////////////////////////////////////////
        // Other pre-flattening optimisations
        
        
        ////////////////////////////////////////////////////////////////////////
        //
        // If we are generating Gecode output, decompose some reified constraints
        // Do this before flattening.
        if (CmdFlags.getGecodetrans()) {
            TransformReifyAlldiff tgo1 = new TransformReifyAlldiff(m);
            m.transform(tgo1);
            
            TransformDecomposeNegativeTable tnt=new TransformDecomposeNegativeTable(m);
            m.transform(tnt);
            // More needed here.
        }
        
        // Some reformulations will be affected by order, so normalise.
        TransformNormalise tnr = new TransformNormalise(m);
        m.transform(tnr);
        
    }

    // can be executed after instancePreFlattening1
    public void squashDomains() {
        Model ipf1 = m.copy();        // Take a copy of the model
        
        boolean tmp_outready = CmdFlags.getOutputReady();
        boolean tmp_afteragg = CmdFlags.getAfterAggregate();

        instancePreFlattening2(true);
        instanceFlattening(-1, true);
        ArrayList<ASTNode> newfinds = postFlattening(-1, true);
        
        // Rescue the FilteredDomainStore object, containing mappings from expressions to aux var names 
        FilteredDomainStore filt=m.filt;
        ASTNode isol=m.incumbentSolution;
        
        // Restore the model.
        m = ipf1;
        
        // Restore the FilteredDomainStore object. 
        m.filt=filt;
        m.incumbentSolution=isol;
        
        // Restore the control flags to their original state (flags should probably eventually be in the model.)
        CmdFlags.setOutputReady(tmp_outready);
        CmdFlags.setAfterAggregate(tmp_afteragg);
        
        // Apply reduced domains in newfinds to the symbol table.
        for (int i =0; i < newfinds.size(); i++) {
            ASTNode id = newfinds.get(i).getChild(0);
            ASTNode dom = newfinds.get(i).getChild(1);
            assert id instanceof Identifier;
            assert dom.isFiniteSet();

            String idname = id.toString();

            if (m.global_symbols.getCategory(idname) == ASTNode.Decision) {
                ASTNode olddom = m.global_symbols.getDomain(idname);
                ASTNode newdom = new Intersect(olddom, dom);
                
                // Intersect correctly casts a boolean set to a non-boolean set when
                // intersecting it with a set of int. Sort out the boolean case.
                // This is a rather unpleasant hack.
                if (olddom.isBooleanSet()) {
                    TransformSimplify ts = new TransformSimplify();
                    newdom = Intpair.makeDomain((ts.transform(newdom)).getIntervalSet(), true);
                }
                m.global_symbols.setDomain(idname, newdom);
            }
            else {
                // It is not a primary decision variable, it should be an aux variable. 
                // Store for use if the same aux var is made again. 
                
                m.filt.auxVarFilteredDomain(idname, dom);
            }
        }
        
        m.simplify();        // Simplifies everything with the symbol table going before the constraints.
    }
    
    public void branchingInstanceFlattening() {
        CmdFlags.currentModel=null;   // For safety don't use the currentModel global. 
        
        // Make an array of transforms.
        ArrayList<TreeTransformer> tryout = new ArrayList<TreeTransformer>();
        
        // At the moment this order is significant.
        tryout.add(new TransformMultiplyOutSum());
        tryout.add(new TransformFactorOutSum());

        tryout.add(new TransformImplicationOr(false));
        tryout.add(new TransformImplicationOr(true));

        tryout.add(new TransformDeMorgans(true));
        tryout.add(new TransformDeMorgans(false));
        tryout.add(new TransformReverseDeMorgans(true));
        tryout.add(new TransformReverseDeMorgans(false));

        tryout.add(new TransformDistributeLogical(true, true));
        tryout.add(new TransformDistributeLogical(false, true));        // Blows up with plotting.
        tryout.add(new TransformFactorLogical(true));
        tryout.add(new TransformFactorLogical(false));

        ArrayList<ArrayList<Boolean>> switches_list = new ArrayList<ArrayList<Boolean>>();

        buildSwitches(switches_list, new ArrayList<Boolean>(), tryout.size());


        // Take a pristine copy before making any branches.
        Model mbackup = m.copy();

        int modelcount =0;

        for (int i =0; i < switches_list.size(); i++) {
            System.out.println("Switches: " + switches_list.get(i));

            boolean skip = false;            // skip this model.
            int skiploc = -1;

            for (int j =0; j < tryout.size(); j++) {
                if (switches_list.get(i).get(j)) {

                    String mod1 = m.toString();
                    boolean modelchanged = m.transform(tryout.get(j));
                    String mod2 = m.toString();

                    if (! modelchanged && ! mod1.equals(mod2)) {
                        CmdFlags.println("Yikes: modelchanged flag wrong.");
                        modelchanged = true;
                    }

                    if (!modelchanged) {
                        // One of the transformations did nothing. Skip this set of transformations,
                        // because there is another set where switches[j] is false.
                        skip = true;
                        skiploc = j;
                        break;
                    }
                }
            }

            if (!skip) {

                modelcount++;
                instanceFlattening(modelcount, false);
                postFlattening(modelcount, false);
            } else {
                assert skiploc > -1;

                // Move forward to next assignment where the prefix 0..skiploc is different.
                //

                while (i < switches_list.size() && switches_list.get(i).get(skiploc)) {
                    i++;
                }


                i--;                // to counteract the i++ in the main loop.
            }

            // Restore the model.
            m = mbackup.copy();

        }

        System.out.println("Total models:" + modelcount);


    }

    public void buildSwitches(ArrayList<ArrayList<Boolean>> switches_list, ArrayList<Boolean> switches, int numSwitches) {
        // numSwitches is the number left to fill in.
        if (numSwitches == 0) {
            switches_list.add(switches);
            return;
        }

        ArrayList<Boolean> false_copy_switches = new ArrayList<Boolean>(switches);
        false_copy_switches.add(false);

        ArrayList<Boolean> true_copy_switches = new ArrayList<Boolean>(switches);
        true_copy_switches.add(true);

        buildSwitches(switches_list, false_copy_switches, numSwitches - 1);
        buildSwitches(switches_list, true_copy_switches, numSwitches - 1);
    }


    // Model number is for when this is called by branchingInstanceFlattening
    // Propagate is for shrinking domains, and will return find statements for the
    // reduced domains.
    public void instanceFlattening(int modelnumber, boolean propagate) {
        
        ////////////////////////////////////////////////////////////////////////
        //
        // Special cases of flattening.
        
        ////////////////////////////////////////////////////////////////////////
        //
        // Variable elimination.
        
        if (CmdFlags.getUseEliminateVars() && !propagate) {
            /*for(int i=0; i<=50; i=i+10) {
                System.out.println("Eliminating vars with increase parameter of : "+i);
                VarElim v=new VarElim(m, i);
                v.eliminateVariables();
            }
            for(int i=2; i<=10; i++) {
                System.out.println("Eliminating vars with scale parameter of : "+i);
                VarElim v=new VarElim(m, 0, i);
                v.eliminateVariables();
            }
            // VarElim v=new VarElim(m, 20);
            VarElim v = new VarElim(m, true);
            v.eliminateVariables();*/
            
        }
        
        if (CmdFlags.getVerbose()) {
            System.out.println("Rules: Normalisation and CSE");
        }
        
        // Sort expressions to help CSE.  This also helps with n-ary CSE by
        // sorting sub-expressions within the N-ary expression.
        TransformNormalise tnr = new TransformNormalise(m);
        
        // CSE in N-ary cts   --- *
        if (CmdFlags.getUseACCSE()) {
            m.transform(tnr);
            // Do N-ary * first.  Needs to be done before TransformTimes.
            // Unfortunately doing this early leads to possibility of making
            // an aux var, then finding (after some other CSEs) it is only used in one place.
            // Perhaps need a reverse flattening transform to deal with this.
            ACCSE c = new ACCSE();
            c.flattenCSEs(m, "*");
            CmdFlags.stats.put("AC-CSE-Times_number", c.numcse);
            CmdFlags.stats.put("AC-CSE-Times_eliminated_expressions", c.countcse);
            CmdFlags.stats.put("AC-CSE-Times_total_size", c.totallength);
            m.simplify();
        }
        
        if (CmdFlags.getUseACCSEAlt()) {
            // Araya, Trombettoni and Neveu algorithm.
            m.transform(tnr);            // Normalise again.

            ICSEProduct cp = new ICSEProduct();

            cp.flattenCSEs(m);
            m.simplify();
        }
        
        ////////////////////////////////////////////////////////////////////////
        //
        // Before flattening, deal with N-ary Times which can't appear in the output.
        // Needs to be done before plain CSE because it can cause  e.g.  ab  and abc
        // to have a standard CSE when N-ary CSE is switched off.
        CmdFlags.setOutputReady(true);
        
        TransformTimes ttimes = new TransformTimes(m);
        m.transform(ttimes);
        
        // Plain CSE  -- Just top level constraints.
        if (CmdFlags.getUseCSE()) {
            m.transform(tnr);            // Normalise again. May not be necessary.
            
            CSETopLevel ctl = new CSETopLevel();
            ctl.flattenCSEs(m);
            CmdFlags.stats.put("CSETopLevel_number", ctl.numcse);
            CmdFlags.stats.put("CSETopLevel_eliminated_expressions", ctl.countcse);
            CmdFlags.stats.put("CSETopLevel_total_size", ctl.totallength);
            m.simplify();
        }
        
        // Subset N-ary CSE. Do this before plain CSE because otherwise NaryCSE
        // will only be able to take out subsets of aux variables.
        if (CmdFlags.getUseACCSE()) {
            ACCSE c = new ACCSE();
            
            m.transform(tnr);
            c.flattenCSEs(m, "\\/");
            CmdFlags.stats.put("AC-CSE-Or_number", c.numcse);
            CmdFlags.stats.put("AC-CSE-Or_eliminated_expressions", c.countcse);
            CmdFlags.stats.put("AC-CSE-Or_total_size", c.totallength);
            m.simplify();
            
            m.transform(tnr);
            c.flattenCSEs(m, "/\\");
            CmdFlags.stats.put("AC-CSE-And_number", c.numcse);
            CmdFlags.stats.put("AC-CSE-And_eliminated_expressions", c.countcse);
            CmdFlags.stats.put("AC-CSE-And_total_size", c.totallength);
            m.simplify();
            
            m.transform(tnr);
            
            if(CmdFlags.getUseActiveACCSE()) {
                ACCSEActiveSum c2=new ACCSEActiveSum();
                c2.flattenCSEs(m);
                CmdFlags.stats.put("Active-AC-CSE-Sum_number", c2.numcse);
                CmdFlags.stats.put("Active-AC-CSE-Sum_eliminated_expressions", c2.countcse);
                CmdFlags.stats.put("Active-AC-CSE-Sum_total_size", c2.totallength);
                CmdFlags.stats.put("Active-AC-CSE-Found", c2.active_ac_cs_found?1:0);
            }
            else {
                c.flattenCSEs(m, "+");
                CmdFlags.stats.put("AC-CSE-Sum_number", c.numcse);
                CmdFlags.stats.put("AC-CSE-Sum_eliminated_expressions", c.countcse);
                CmdFlags.stats.put("AC-CSE-Sum_total_size", c.totallength);
            }
        }
        
        if (CmdFlags.getUseACCSEAlt()) {
            // Araya, Trombettoni and Neveu algorithm for sums only.
            m.transform(tnr);            // Normalise again.
            ICSESum c = new ICSESum();
            
            c.flattenCSEs(m);
            m.simplify();
            
            // Doesn't do the stats yet.
        }
        
        ////////////////////////////////////////////////////////////////////////
        //
        // Remove redundant constraints that were added earlier, derived from AllDiff or GCC
        // if they were not modified by AC-CSE.
        
        if(CmdFlags.getUseACCSE() || CmdFlags.getUseACCSEAlt()) {
            //  Delete implied sum constraints based on AllDiffs and GCCs. Only when using AC-CSE
            TransformAlldiffGCCSumDelete tas = new TransformAlldiffGCCSumDelete(m);
            m.transform(tas);
        }
        
        ////////////////////////////////////////////////////////////////////////
        //
        //   Decomposition of constraints for SAT
        
        if(CmdFlags.getSattrans() && !propagate) {
            // Decompose some of the global constraints for SAT encoding
            decomposeSatEncoding();
        }
        
        // Plain CSE or Active CSE.
        if (CmdFlags.getUseCSE() || CmdFlags.getUseActiveCSE()) {
            m.transform(tnr);            // Normalise again.
            
            if (CmdFlags.getUseActiveCSE()) {
                CSEActive c = new CSEActive();
                c.flattenCSEs(m);
                m.simplify();
                CmdFlags.stats.put("CSE_active_number", c.numcse);
                CmdFlags.stats.put("CSE_active_eliminated_expressions", c.countcse);
                CmdFlags.stats.put("CSE_active_total_size", c.totallength);
            } else {
                // Identical-CSE.
                CSE c = new CSE();
                c.flattenCSEs(m);
                m.simplify();
                CmdFlags.stats.put("CSE_number", c.numcse);
                CmdFlags.stats.put("CSE_eliminated_expressions", c.countcse);
                CmdFlags.stats.put("CSE_total_size", c.totallength);
            }
        }
        
        if (CmdFlags.getVerbose()) {
            System.out.println("Model may have changed by CSE. Model after rule application:\n" + m.toString());
        }
        
        // Other special cases of flattening. Probably not needed with -deletevars.
        
        TransformEqual t38 = new TransformEqual(m);
        m.transform(t38);
        
        TransformEqualConst t39 = new TransformEqualConst();
        m.transform(t39);
        
        ////////////////////////////////////////////////////////////////////////
        //
        // General flattening.
        
        TransformToFlat t4 = new TransformToFlat(m, propagate);
        m.transform(t4);
        
        //  Some further flattening for Flatzinc/Gecode output.
        if(CmdFlags.getGecodetrans() && !propagate) {
            TransformToFlatGecode tfg=new TransformToFlatGecode(m);
            m.transform(tfg);
        }
    }

    //
    public ArrayList<ASTNode> postFlattening(int modelnumber, boolean propagate) {
        
        ////////////////////////////////////////////////////////////////////////
        //  Remove types that have no output  to any solver. 
        
        TransformMappingToTable tmtt=new TransformMappingToTable();
        m.transform(tmtt);
        
        ////////////////////////////////////////////////////////////////////////
        // Branch for Gecode output.
        if (CmdFlags.getGecodetrans() && !propagate) {
            gecodeFlattening();
            System.exit(0);
        }

        ////////////////////////////////////////////////////////////////////////
        // Branch for Minizinc output
        if (CmdFlags.getMinizinctrans() && !propagate) {
            minizincOutput();
            System.exit(0);
        }

        ////////////////////////////////////////////////////////////////////////
        // Branch for SAT output
        if(CmdFlags.getSattrans() && !propagate) {
            //  Post-flattening decomposition of some constraints. 
            
            decomposeSatEncodingFlat();
            
            m.simplify();  // Fix problem with unit vars when outputting sat. 
            
            if(CmdFlags.getTestSolutions()) {
                CmdFlags.checkSolModel=m.copy();
            }
            
            // Discover the variables that need a direct encoding, in addition to the order encoding. 
            TransformCollectSATDirect tcsd=new TransformCollectSATDirect(m);
            tcsd.transform(m.constraints);
            
            CmdFlags.printlnIfVerbose("About to do m.setupSAT");
            
            boolean satenc=m.setupSAT();   //  Create the satModel object and encode the variables.
            
            if(!satenc) {
                // Create .info and .infor files. 
                
                Stats stats=new Stats();
                stats.putValue("SavileRowTotalTime", String.valueOf(((double) System.currentTimeMillis() - CmdFlags.startTime) / 1000));
                stats.putValue("SavileRowClauseOut", "1");
                stats.makeInfoFiles();
                
                CmdFlags.errorExit("Failed when writing SAT encoding to file.");
            }
            
            CmdFlags.printlnIfVerbose("Done m.setupSAT");
            
            //  Do the rewrites that make SATLiterals.
            TransformSATEncoding tse=new TransformSATEncoding(m);
            m.transform(tse);
            
            satOutput();
            System.exit(0);
        }
        
        // Get rid of sum equal for Minion.
        TransformSumEq t5 = new TransformSumEq();
        m.transform(t5);
        
        // Experiment.
        // TransformProductToTable tptt=new TransformProductToTable(m);
        // m.transform(tptt);
        
        ////////////////////////////////////////////////////////////////////////
        //  Minion output. 
        
        if(CmdFlags.getMakeTables() && !propagate) {
            makeTables();
        }
        
        m.simplify();
        
        // Warm start for optimisation
        if(CmdFlags.getOptWarmStart() && m.objective!=null && propagate) {
            MinionSolver ws = new MinionSolver();
            try {
                Solution sol=ws.optWarmStart(m);
                if(sol!=null) {
                    // Store the solution in case no better one is found, and it
                    // is needed for output. 
                    m.incumbentSolution=sol;
                    
                    // Get the value of the optimisation variable from sol and
                    // add a new constraint 
                    long optval=sol.optval;
                    ASTNode newcon;
                    ASTNode obvar=m.objective.getChild(0);
                    if(m.objective instanceof Minimising) {
                        newcon=new Less(obvar, new NumberConstant(optval));
                    }
                    else {
                        newcon=new Less(new NumberConstant(optval), obvar);
                    }
                    System.out.println("Adding warm start constraint: "+newcon); 
                    // Bound the optimisation variable. 
                    m.constraints.setChild(0, new And(m.constraints.getChild(0), newcon));
                    m.simplify();  // Make the top and flat again. 
                }
            }
            catch(Exception e) {
            }
        }
        
        StringBuffer b = new StringBuffer();

        m.toMinion(b);

        assert CmdFlags.minionfile != null;

        String minfilename = CmdFlags.minionfile;
        if (modelnumber > -1) {
            minfilename = minfilename + "." + modelnumber;
        }

        try {
            BufferedWriter out;
            out = new BufferedWriter(new FileWriter(minfilename));
            out.write(b.toString());
            out.close();
        } catch (IOException e) {
            System.out.println("Could not open file for Minion output.");
            CmdFlags.exit();
        }
        
        CmdFlags.println("Created output file "+ (propagate?"for domain filtering ":"") + minfilename);
        
        if (propagate) {
            MinionSolver min = new MinionSolver();
            try {
                return min.reduceDomains(CmdFlags.getMinion(), minfilename, m);
            } catch (java.io.IOException e) {
                CmdFlags.println("Could not run Minion: " + e);
                CmdFlags.exit();
            } catch (java.lang.InterruptedException e2) {
                CmdFlags.println("Could not run Minion: " + e2);
                CmdFlags.exit();
            }
        } else if (CmdFlags.getRunSolver()) {
            MinionSolver min = new MinionSolver();
            
            try {
                min.findSolutions(CmdFlags.getMinion(), minfilename, m);
            } catch (java.io.IOException e) {
                CmdFlags.println("Could not run Minion: " + e);
                CmdFlags.exit();
            } catch (java.lang.InterruptedException e2) {
                CmdFlags.println("Could not run Minion: " + e2);
                CmdFlags.exit();
            }
        }
        return null;
    }
    
    //  -make-tables flag
    private void makeTables() {
        // Make a table with the scope specified on the command line. 
        
        MinionSolver min = new MinionSolver();
        
        ArrayList<ASTNode> scope=new ArrayList<ASTNode>();
        
        ArrayList<ASTNode> allDecisionVars=new ArrayList<ASTNode>();
        
        categoryentry c=m.global_symbols.category_first;
        while(c!=null) {
            if(c.cat==ASTNode.Decision) {
                //   Deliberately NOT aux vars for now. 
                allDecisionVars.add(new Identifier(c.name, m.global_symbols));
            }
            c=c.next;
        }
        
        for(int i=0; i<CmdFlags.make_tables_scope.size(); i++) {
            scope.add(allDecisionVars.get(CmdFlags.make_tables_scope.get(i)));
        }
        
        ASTNode t=null;
        try {
            t=min.makeTable(m, scope);
        }
        catch( Exception e) {
            System.out.println(e);
            e.printStackTrace(System.out);
        }
        
        m.constraints=new Top(new And(m.constraints.getChild(0), t));
        m.simplify();
    }
    
    private void destroyMatrices() {
        boolean has_changed = true;
        while (has_changed) {
            has_changed = false;

            HashMap<String, ASTNode> doms = m.global_symbols.getDomains();
            Iterator<Map.Entry<String, ASTNode>> itr = doms.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry<String, ASTNode> a = itr.next();
                if (a.getValue() instanceof MatrixDomain) {
                    if (m.global_symbols.getCategory(a.getKey()) == ASTNode.Decision) {
                        TransformMatrixToAtoms tmta = new TransformMatrixToAtoms(a.getKey(), m);
                        m.transform(tmta);
                        has_changed = true;
                        break;
                    }
                }
                if (a.getValue() instanceof HoleyMatrixDomain) {
                    if (m.global_symbols.getCategory(a.getKey()) == ASTNode.Decision) {
                        TransformHoleyMatrixToAtoms tmta = new TransformHoleyMatrixToAtoms(a.getKey(), m);
                        m.transform(tmta);
                        has_changed = true;
                        break;
                    }
                }
            }
        }
    }
    
    private void removeMatrixIndexedMatrices() {
        boolean has_changed = true;
        while (has_changed) {
            has_changed = false;

            HashMap<String, ASTNode> doms = m.global_symbols.getDomains();
            Iterator<Map.Entry<String, ASTNode>> itr = doms.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry<String, ASTNode> a = itr.next();
                if (a.getValue() instanceof MatrixDomain) {
                    if (m.global_symbols.getCategory(a.getKey()) == ASTNode.Decision) {
                        boolean has_matrix_index=false;
                        for(int i=3; i<a.getValue().numChildren(); i++) {
                            if(a.getValue().getChild(i) instanceof MatrixDomain) has_matrix_index=true;
                        }
                        if(has_matrix_index) {
                            TransformMatrixIndexedMatrix tmim = new TransformMatrixIndexedMatrix(a.getKey(), m);
                            m.transform(tmim);
                            has_changed = true;
                            break;
                        }
                    }
                }
            }
        }
    }
    
    // If the -dominion cmdline option given
    private void classLevelFlattening() {
        System.out.println(m.toString());

        CmdFlags.setAfterAggregate(true);

        // Normalise matrix indices
        // Need to do real normalisation here, because this is before matrix deref
        // is transformed into an arith expression and element constraint
        HashMap<String, ASTNode> doms = m.global_symbols.getDomains();
        Iterator<Map.Entry<String, ASTNode>> itr = doms.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, ASTNode> a = itr.next();
            if (a.getValue() instanceof MatrixDomain) {
                TransformMatrixIndicesClass ti = new TransformMatrixIndicesClass(0, m, a.getKey());
                m.transform(ti);
                System.out.println(m.toString());
            }
        }

        ////////////////////////////////////////////////////////////////////////
        //
        // If objective function is not a solitary variable, do one flattening op on it.
        // This is done early because objective has no relational context -- therefore various transformations break on it.

        if (m.objective != null) {
            ASTNode ob = m.objective.getChild(0);
            if (!ob.isConstant() && ! (ob instanceof Identifier)) {
                boolean flatten = true;
                if (ob instanceof MatrixDeref || ob instanceof SafeMatrixDeref) {
                    flatten = false;
                    for (int i =1; i < ob.numChildren(); i++) {
                        if (ob.getChild(i).getCategory() == ASTNode.Decision) {
                            flatten = true;
                        }
                    }
                }

                if (flatten) {
                    PairASTNode bnds = ob.getBoundsAST();
                    ASTNode auxvar = m.global_symbols.newAuxiliaryVariable(bnds.e1, bnds.e2);
                    ASTNode flatcon = new ToVariable(ob, auxvar);
                    m.global_symbols.auxVarRepresentsConstraint(auxvar.toString(), ob.toString());
                    m.objective.setChild(0, auxvar);
                    m.constraints.setChild(0, new And(flatcon, m.constraints.getChild(0)));
                }
            }
        }

        ////////////////////////////////////////////////////////////////////////
        // Push And through Forall to produce better Dominion models.
        TransformForallAndToAndForall tfor = new TransformForallAndToAndForall();
        m.transform(tfor);

        ////////////////////////////////////////////////////////////////////////
        // Transform matrix derefs into non-flat element and index aux var.
        TransformMatrixDerefClass tmd = new TransformMatrixDerefClass(m);
        m.transform(tmd);

        ////////////////////////////////////////////////////////////////////////
        // Pre-flattening rearrangement

        TransformOccurrence t35 = new TransformOccurrence();
        m.transform(t35);

        ////////////////////////////////////////////////////////////////////////
        // Sums

        TransformSumEqualSum tses = new TransformSumEqualSum(m);
        m.transform(tses);

        // Same as instance transformation.
        // 1. Transform sum < X  into sum+1 <= X

        TransformSumLess t36 = new TransformSumLess();
        m.transform(t36);

        // 2. Transform sum1 <= sum2 into (sum1 - sum2) <= 0
        // so that it goes into one sumleq constraint.

        TransformSumLeq t37 = new TransformSumLeq();
        m.transform(t37);

        ////////////////////////////////////////////////////////////////////////
        // Mappers

        if (CmdFlags.getUseMappers()) {
            TransformSumToShift tsts = new TransformSumToShift(m);
            m.transform(tsts);

            TransformProductToMult tptm = new TransformProductToMult(m);
            m.transform(tptm);
        }
        if (CmdFlags.getUseMinionMappers()) {
            // Just deal with quantified sum -- weighted sum is already OK.
            TransformProductToMultInQSum tptm = new TransformProductToMultInQSum(m);
            m.transform(tptm);
        }
        if (!CmdFlags.getUseMappers() && !CmdFlags.getUseMinionMappers()) {
            // Turn weighted sum into plain sum
            TransformWSumToSum twsts = new TransformWSumToSum(m);
            m.transform(twsts);
        }

        ////////////////////////////////////////////////////////////////////////
        //
        // Special cases of flattening

        // CSE goes here in instance-level sequence
        if (CmdFlags.getUseCSE()) {
            CSEClassIdentical c = new CSEClassIdentical();            // This doesn't work.
            c.flattenCSEs(m);
            m.simplify();
        }

        ////////////////////////////////////////////////////////////////////////
        //
        // Before general flattening, deal with N-ary Times which can't appear in the output.
        CmdFlags.setOutputReady(true);

        TransformTimes ttimes = new TransformTimes(m);
        m.transform(ttimes);

        // More special cases of flattening.

        TransformEqualClass t38 = new TransformEqualClass(m);
        m.transform(t38);

        TransformEqualConstClass tec = new TransformEqualConstClass();
        m.transform(tec);

        ////////////////////////////////////////////////////////////////////////
        //
        // General flattening.

        TransformToFlatClass tf = new TransformToFlatClass(m);
        m.transform(tf);

        ////////////////////////////////////////////////////////////////////////
        // Tidy up

        TransformConstMatrixClass tcmc = new TransformConstMatrixClass(m);
        m.transform(tcmc);

        System.out.println("**** Completed class-level flattening ****");
        System.out.println(m.toString());

        // Now shift matrices to 0-based again, to deal with the new ones.
        // Can do this because all matrix derefs have only parameter expressions
        // as indices.
        doms = m.global_symbols.getDomains();
        itr = doms.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<String, ASTNode> a = itr.next();
            if (a.getValue() instanceof MatrixDomain) {
                boolean needsShift = false;
                ArrayList<ASTNode> indices = ((MatrixDomain) a.getValue()).getMDIndexDomains();
                for (int i =0; i < indices.size(); i++) {
                    if (! indices.get(i).getBoundsAST().e1.equals(new NumberConstant(0))) {
                        needsShift = true;
                        break;
                    }
                }

                if (needsShift) {
                    CmdFlags.println("About to normalise indices of matrix: " + a.getKey());

                    TransformMatrixIndicesClass ti = new TransformMatrixIndicesClass(0, m, a.getKey());
                    m.transform(ti);
                    System.out.println(m.toString());
                }
            }
        }

        m.simplify();

        System.out.println("************************");
        System.out.println("**** After Simplify ****");
        System.out.println("************************");
        System.out.println(m.toString());

        System.out.println("************************");
        System.out.println("**** Dominion output ***");
        System.out.println("************************");
        StringBuffer b = new StringBuffer();
        m.toDominion(b);
        System.out.println(b.toString());

        try {
            BufferedWriter out;
            out = new BufferedWriter(new FileWriter(CmdFlags.dominionfile));
            out.write(b.toString());
            out.close();
        } catch (IOException e) {
            System.out.println("Could not open file for Dominion output.");
            CmdFlags.exit();
        }

        // TODO
        // 2. fix aux6[i]=aux7 malarky.
    }

    // If the -gecode cmdline option given
    private void gecodeFlattening() {
        // Flattening is done already.
        TransformSumEqToSum t1 = new TransformSumEqToSum();
        m.transform(t1);
        
        // Get rid of some reified constraints where Gecode does not implement them.
        TransformReifyMin trm = new TransformReifyMin(m);
        m.transform(trm);
        
        TransformAbsReify tar = new TransformAbsReify(m);
        m.transform(tar);
        
        m.simplify();
        
        // THE VERY LAST THING must be to collect bool and int vars.
        TransformCollectBool tcb = new TransformCollectBool(m);
        m.transform(tcb);

        StringBuffer b = new StringBuffer();
        m.toFlatzinc(b);

        try {
            BufferedWriter out;
            out = new BufferedWriter(new FileWriter(CmdFlags.gecodefile));
            out.write(b.toString());
            out.close();
        } catch (IOException e) {
            System.out.println("Could not open file for Gecode flatzinc output.");
            CmdFlags.exit();
        }

        CmdFlags.println("Created output file " + CmdFlags.gecodefile);

        if (CmdFlags.getRunSolver()) {
            GecodeSolver min = new GecodeSolver();

            try {
                min.findSolutions(CmdFlags.getGecode(), CmdFlags.gecodefile, m);
            } catch (java.io.IOException e) {
                CmdFlags.println("Could not run Gecode: " + e);
                CmdFlags.exit();
            } catch (java.lang.InterruptedException e2) {
                CmdFlags.println("Could not run Gecode: " + e2);
                CmdFlags.exit();
            }
        }

    }

    // If the -minizinc cmdline option given
    private void minizincOutput() {
        // Flattening is done already.
        
        // THE VERY LAST THING must be to collect bool and int vars.
        TransformCollectBool tcb = new TransformCollectBool(m);
        m.transform(tcb);
        
        StringBuffer b = new StringBuffer();
        m.toMinizinc(b);

        try {
            BufferedWriter out;
            out = new BufferedWriter(new FileWriter(CmdFlags.minizincfile));
            out.write(b.toString());
            out.close();
        } catch (IOException e) {
            System.out.println("Could not open file for minizinc output.");
            CmdFlags.exit();
        }

        CmdFlags.println("Created output file " + CmdFlags.minizincfile);

    }
    
    // Decompose some constraints for SAT output. Occurs before flattening.
    private void decomposeSatEncoding() {
        
        //  Two options for alldiff -- atmost constraints or pairwise binary decomposition.
        if(true) {
            TransformAlldiffToAtmost taa= new TransformAlldiffToAtmost(m);
            m.transform(taa);
        }
        else {
            TransformDecomposeAlldiff tda= new TransformDecomposeAlldiff(m);
            m.transform(tda);
        }
        
        TransformGCCToSums tgts=new TransformGCCToSums();
        m.transform(tgts);
        
        TransformOccurrenceToSum tots=new TransformOccurrenceToSum();
        m.transform(tots);
        
        if(CmdFlags.getSatDecompCSE()) {
            TransformDecomposeLex2 tdlx=new TransformDecomposeLex2(m);
            m.transform(tdlx);
        }
        else {
            TransformDecomposeLex tdlx=new TransformDecomposeLex(m);
            m.transform(tdlx);
        }
    }
    
    //  Further decompositions that are applied after flattening/CSE.
    private void decomposeSatEncodingFlat() {
        
        // Decompose constraints made from functions by ToVariable. 
        TransformDecomposeMinMax tdmm=new TransformDecomposeMinMax(m);
        m.transform(tdmm);
        
        if(false) {
            TransformElementForSAT tefs=new TransformElementForSAT(m);
            m.transform(tefs);
        }
        else {
            TransformElementForSAT2 tefs=new TransformElementForSAT2(m);
            m.transform(tefs);
        }
        
        //  Break up sums for SAT output. 
        if(!CmdFlags.getSatAlt()) {
            if(false) {
                // Sort sums by coefficient size.
                TransformSortWeightedSum2 tsort=new TransformSortWeightedSum2();
                m.transform(tsort);
                
                TransformBreakupSum tbs=new TransformBreakupSum(m);
                m.constraints=tbs.transform(m.constraints);
                
                TransformToFlat ttf=new TransformToFlat(m, false); // Assumes SAT output is not used for preprocessing. 
                m.transform(ttf);
            }
            else {
                //   Tree decomp of sum.
                TransformBreakupSum2 tbs=new TransformBreakupSum2(m);
                m.transform(tbs);
            }
            
            // Get rid of sum equal, leaving only inequalities
            TransformSumEq t5 = new TransformSumEq();
            m.transform(t5);
            
            // Rearrange sums
            TransformSumForSAT t1=new TransformSumForSAT();
            m.transform(t1);
            
            // Sort sums by coefficient -- prob. to reduce number of iterations in order encoding of ct.
            TransformWeightedSumForSAT t6=new TransformWeightedSumForSAT();
            m.transform(t6);
        }
        else {
            //  This branch treats sums like product etc. End up with flat x+y=z type constraints
            //  that are then encoded using the toSATWithAuxVar method in WeightedSum -- same as product etc. 
            // Sort sums by coefficient size.
            TransformSortWeightedSum2 tsort=new TransformSortWeightedSum2();
            m.transform(tsort);
            
            TransformBreakupSum tbs=new TransformBreakupSum(m);
            m.constraints=tbs.transform(m.constraints);
            
            TransformToFlat ttf=new TransformToFlat(m, false); // Assumes SAT output is not used for preprocessing. 
            m.transform(ttf);
        }
    }
    
    // If the -sat cmdline option given
    private void satOutput() {
        boolean satenc=m.toSAT();
        
        if(!satenc) {
            // Create .info and .infor files. 
            Stats stats=new Stats();
            stats.putValue("SavileRowTotalTime", String.valueOf(((double) System.currentTimeMillis() - CmdFlags.startTime) / 1000));
            stats.putValue("SavileRowClauseOut", "1");
            stats.makeInfoFiles();
            
            CmdFlags.errorExit("Failed when writing SAT encoding to file.");
        }
        
        CmdFlags.println("Created output SAT file " + CmdFlags.satfile);
        
        if(CmdFlags.getRunSolver()) {
            SATSolver solver;
            
            if(CmdFlags.getSatFamily().equals("minisat")) {
                solver=new MinisatSATSolver(m);
            }
            else {
                assert CmdFlags.getSatFamily().equals("lingeling");
                solver=new LingelingSATSolver(m);
            }
            
            try {
                solver.findSolutions(CmdFlags.getSatSolver(), CmdFlags.satfile, m);
            } catch (Exception e) {
                CmdFlags.errorExit("Could not run SAT solver: " + e);
            }
            
            //  Delete the dimacs file because it may be very large. 
            File f = new File(CmdFlags.satfile);
            if (f.exists()) f.delete();
        }
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Deal with statements in the preamble.
    
    private void processGiven(ASTNode giv) {
        ASTNode id = giv.getChild(0);
        String idname = ((Identifier) id).getName();
        ASTNode dom = giv.getChild(1);

        // Insert into symbol table as a parameter.
        if (m.global_symbols.hasVariable(idname)) {
            CmdFlags.errorExit("Symbol " + idname + " declared twice.");
        }
        m.global_symbols.newVariable(idname, dom, ASTNode.Parameter);

        if (!CmdFlags.getClasstrans()) {            // If doing instance transformation, look in the parameter file.
            int num_lettings =0;
            ASTNode param = null;
            for (ASTNode p : parameters) {
                if (((Identifier) ((Letting) p).getChildren().get(0)).getName().equals(idname)) {
                    param = p;
                    num_lettings++;
                }
            }
            if (num_lettings != 1) {
                CmdFlags.errorExit("Too many or zero lettings of parameter variable " + idname + " in parameter file.");
            }
            
            processLetting(param);
            parameters.remove(param);
        }
    }
    
    private void processLetting(ASTNode let) {
        assert let instanceof Letting;
        
        ASTNode id = let.getChild(0);
        String idname = ((Identifier) id).getName();
        if (m.global_symbols.hasVariable(idname) && m.global_symbols.getCategory(idname) != ASTNode.Parameter) {
            CmdFlags.errorExit("Symbol " + idname + " declared more than once.");
        }
        ASTNode value = let.getChild(1);
        
        if (value.getCategory() > ASTNode.Quantifier) {
            CmdFlags.errorExit("In statement: " + let, "Right-hand side contains an identifier that is not a constant or parameter.");
        }
        
        if (value instanceof CompoundMatrix || value instanceof EmptyMatrix) {
            // Put into symbol table
            // No need for domain in letting because the matrix literal has been adjusted to be consistent with the domain in the letting. 
            m.global_symbols.newConstantMatrix(idname, value);
            
            // Adding a constant matrix may make some lettings appear, inferred from the dimensions.
            
            ArrayList<ASTNode> newlets = m.global_symbols.makeLettingsConstantMatrix(idname);
            for (ASTNode l2 : newlets) {
                processLetting(l2);
            }
            
            // Now fit the matrix literal to the domain in the symbol table if this is possible.
            // The domain in the symbol table either came from a letting or a given.
            // If it came from the letting, we are repeating work here (unfortunate but not wrong).
            // If it came from the given, we need to do this.
            m.global_symbols.correctIndicesConstantMatrix(idname);
            
            // Substitute everywhere except the second child of a table/negativetable constraint. 
            m.substituteExceptTable(let.getChild(0), m.global_symbols.getConstantMatrix(idname));
        }
        else {            // Substitute it everywhere.
            // m.global_symbols.newVariable(idname, ASTNode.Constant);  // NEED to do something here -- take the given out of the s-table and replace it with a constant.
            m.substitute(let.getChild(0), value.copy());
        }
    }
    
    private void processFind(ASTNode find) {
        assert find instanceof Find;
        ASTNode id = find.getChild(0);
        String idname = ((Identifier) id).getName();
        if (m.global_symbols.hasVariable(idname)) {
            CmdFlags.println("ERROR: Symbol " + idname + " declared more than once.");
            CmdFlags.exit();
        }

        if (find.getChild(1).getCategory() > ASTNode.Quantifier) {
            CmdFlags.println("ERROR: In statement : " + find);
            CmdFlags.println("ERROR: Right-hand side contains an identifier that is not a constant or parameter.");
            CmdFlags.exit();
        }
        m.global_symbols.newVariable(idname, find.getChild(1), ASTNode.Decision);
    }

    private void processWhere(ASTNode a) {
        if (CmdFlags.getClasstrans()) {
            // Should store the where and output it. But there is no where statement in DIL.
            return;
        } else {
            a = a.getChild(0);            // get the actual statement
            // Quantifier expressions should already have been unrolled by now.
            if (a.getCategory() > ASTNode.Quantifier) {
                CmdFlags.println("ERROR: In statement: where " + a);
                CmdFlags.println("ERROR: Contains an identifier that is not a constant or parameter.");
                CmdFlags.exit();
            }
            if (! a.equals(new BooleanConstant(true))) {
                CmdFlags.println("ERROR: In statement: where " + a);
                CmdFlags.println("ERROR: Does not evaluate to true.");
                CmdFlags.exit();
            }
        }
    }

    private void processDim(ASTNode a) {
        // Put the dim into the symbol table.
        ASTNode id = a.getChild(0);

        if (m.global_symbols.hasVariable(id.toString())) {
            CmdFlags.println("ERROR: In statement: " + a);
            CmdFlags.println("ERROR: Contains an identifier that is already defined.");
            CmdFlags.exit();
        }

        // Add a placeholder.
        m.global_symbols.newDim(id.toString(), a.getChild(1), ((Dim) a).dimensions);
    }

    private void processForallFind(ASTNode a) {
        // Put the dim into the symbol table.

        // dig out the name.
        ASTNode temp = a;
        while (temp instanceof ForallExpression) {
            temp = temp.getChild(2);
        }

        temp = temp.getChild(0);
        assert temp instanceof MatrixDeref;
        String name = temp.getChild(0).toString();

        if (! m.global_symbols.hasVariable(name)) {
            CmdFlags.println("ERROR: In statement: " + a);
            CmdFlags.println("ERROR: Forall-Find without preceding matching dim statement.");
            CmdFlags.exit();
        }

        m.global_symbols.newForallFind(name, a);
    }


    // Takes a letting and repairs the index domains in the constant
    // matrix using the matrix domain in the letting (if there is one).
    private void fixIndexDomainsLetting(ASTNode a) {
        if (a.numChildren() == 3) {
            ASTNode mat = a.getChild(1);
            if (mat instanceof CompoundMatrix || mat instanceof EmptyMatrix) {
                Pair<ASTNode, Boolean> p = SymbolTable.fixIndicesConstantMatrix(a.getChild(2), mat.copy());
                if (p.getSecond()) {
                    System.out.println("WARNING: The index domains in the matrix literal do not match");
                    System.out.println("WARNING: the given matrix domain in the following letting statement:");
                    System.out.println("WARNING: " + a);
                }

                a.setChild(1, p.getFirst());
            }
        }
    }
    
    public ModelContainer copy() {
        Model mcopy=m.copy();
        ArrayList<ASTNode> paramcopy=new ArrayList<ASTNode>();
        TransformFixSTRef tf=new TransformFixSTRef(mcopy.global_symbols);
        for(int i=0; i<parameters.size(); i++) {
            paramcopy.add(tf.transform(parameters.get(i)));
        }
        return new ModelContainer(mcopy, paramcopy);
    }
    
    public void writeModelAsJSON(Model m) {
        SymmetryBreaker  s = new SymmetryBreaker ();
        s.detectAndBreakSymmetries(m);
        m.simplify();
    }
}
