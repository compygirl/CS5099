/*
* Minion http://minion.sourceforge.net
* Copyright (C) 2006-09
*
* This program is free software; you can redistribute it and/or
* modify it under the terms of the GNU General Public License
* as published by the Free Software Foundation; either version 2
* of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program; if not, write to the Free Software
* Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

#ifndef GCC_COMMON_H
#define GCC_COMMON_H

#include <stdlib.h>
#include <iostream>
#include <vector>
#include <deque>
#include <algorithm>
#include <utility>
#include "alldiff_gcc_shared.h"

// for NotOccurrenceEqualConstraint, used in reverse_constraint,
#include "constraint_occurrence.h"
#include "../dynamic_constraints/dynamic_new_or.h"


//#define GCCPRINT(x) cout << x << endl
#define GCCPRINT(x)

#define SPECIALQUEUE

// should be called dynamic partitioning
#define SCC
#define SCCCARDS

#define INCREMENTALMATCH

//Incremental graph -- maintains adjacency lists for values and vars
#define UseIncGraph 1

// Does not trigger itself if this is on, and incgraph is on.
#define ONECALL

// When using Regin's algorithm, in the Ford-Fulkerson algorithm, use the
// transpose graph in the second stage (to complete the matching within upper
// bounds).
#define UseTranspose 1

// use the algorithm from Quimper et al. to prune the target variables.
// requires UseIncGraph and not SCC
//#define QUIMPER

// Use WL's to trigger when support for a bound of a cap var is lost.
// Support is a matching.  Uses boundsupported array.
//#define CAPBOUNDSCACHE

// Count domain of triggering variable to avoid running Tarjan's algo.
// Only implemented with SCCs and adjacency lists.
// Can't think of any criteria other than |SCCvars|-1
// DOMAIN COUNTING IS INCORRECT, DO NOT USE.
#define DomainCounting 0

// Use internal dynamic triggers as described in paper.
#define InternalDT 0

// Requires SCC to be defined. Only splits off unit SCCs from the current
// SCC. This is the Gecode implementation.
#define RemoveAssignedVars 0

// When this is defined true, it never reads/chages the cardinality variables
// or reads the values list.
// MUST be used as gcc(vars, [], []) i.e. no capacity variables and no vals.
// it just takes 0-1 as the range for every value.
#define SimulateAlldiff 0

// Note on semantics: GCC only restricts those values which are 'of interest',
// it does not put any restriction on the number of other values.


/// Kind of Blue my Miles Davis --- slowed down bebop and rendered it 'sweet'

template<typename VarArray, typename CapArray, bool Strongcards>
struct GCC : public FlowConstraint<VarArray, UseIncGraph>
{
    using FlowConstraint<VarArray, UseIncGraph>::stateObj;
    using FlowConstraint<VarArray, UseIncGraph>::constraint_locked;

    using FlowConstraint<VarArray, UseIncGraph>::adjlist;
    using FlowConstraint<VarArray, UseIncGraph>::adjlistlength;
    using FlowConstraint<VarArray, UseIncGraph>::adjlistpos;
    using FlowConstraint<VarArray, UseIncGraph>::adjlist_remove;
    using FlowConstraint<VarArray, UseIncGraph>::check_adjlists;

    using FlowConstraint<VarArray, UseIncGraph>::dynamic_trigger_start;
    using FlowConstraint<VarArray, UseIncGraph>::var_array;
    using FlowConstraint<VarArray, UseIncGraph>::dom_min;
    using FlowConstraint<VarArray, UseIncGraph>::dom_max;
    using FlowConstraint<VarArray, UseIncGraph>::numvars;
    using FlowConstraint<VarArray, UseIncGraph>::numvals;
    using FlowConstraint<VarArray, UseIncGraph>::varvalmatching;

    using FlowConstraint<VarArray, UseIncGraph>::varinlocalmatching;
    using FlowConstraint<VarArray, UseIncGraph>::valinlocalmatching;
    using FlowConstraint<VarArray, UseIncGraph>::invprevious;

    using FlowConstraint<VarArray, UseIncGraph>::initialize_hopcroft;

    using FlowConstraint<VarArray, UseIncGraph>::hopcroft2_setup;
    using FlowConstraint<VarArray, UseIncGraph>::hopcroft_wrapper2;
    using FlowConstraint<VarArray, UseIncGraph>::hopcroft2;
    using FlowConstraint<VarArray, UseIncGraph>::augpath;

    GCC(StateObj* _stateObj, const VarArray& _var_array, const CapArray& _capacity_array, vector<DomainInt> _val_array) :
    FlowConstraint<VarArray, UseIncGraph>(_stateObj, _var_array),
    capacity_array(_capacity_array)//, val_array(_val_array)
    #if InternalDT
    ,idt(_stateObj, numvars, numvals, _var_array)
    #endif
    ,SCCSplit(_stateObj, numvars+numvals)
    {
        for(SysInt i = 0; i < (SysInt)_val_array.size(); ++i)
            val_array.push_back(checked_cast<SysInt>(_val_array[i]));

        CheckNotBound(_var_array, "gcc");
        CHECK(capacity_array.size()==val_array.size(), "GCC: Vector of values and vector of cardinality variables must be same length.");

        for(SysInt i=0; i < (SysInt)val_array.size(); i++)
        {
            for(SysInt j=i+1; j < (SysInt)val_array.size(); j++)
            {
                CHECK(val_array[i]!=val_array[j], "GCC: Repeated values are not allowed in value list.");
            }
        }

        usage.resize(numvals, 0);

        lower.resize(numvals, 0);
        if(SimulateAlldiff)
        {   // Pretend to be a gacalldiff.
            upper.resize(numvals, 1);
            if(capacity_array.size()!=0) { cout <<"gcc+SimulateAlldiff requires 0 cap vars."<<endl; abort(); }
        }
        else
        {   // Normal case.
            upper.resize(numvals, numvars);
        }

        prev.resize(numvars+numvals);

        initialize_hopcroft();
        initialize_tarjan();

        // SCC data structures
        SCCs.resize(numvars+numvals);
        varToSCCIndex.resize(numvars+numvals);
        for(SysInt i=0; i<numvars+numvals; i++)
        {
            SCCs[i]=i;
            varToSCCIndex[i]=i;
        }

        vars_in_scc.reserve(numvars);
        vals_in_scc.reserve(numvals);
        // In case we are not using SCCs, fill the var and val arrays.
        vars_in_scc.clear();
        for(SysInt i=0; i<numvars; i++)
        {
            vars_in_scc.push_back(i);
        }
        vals_in_scc.clear();
        for(SysInt i=dom_min; i<=dom_max; i++)
        {
            vals_in_scc.push_back(i);
        }

        to_process.reserve(numvars+numvals);
        sccs_to_process.reserve(numvars+numvals);

        // Array to let us find the appropriate capacity variable for a value.
        val_to_cap_index.resize(numvals);
        for(SysInt i=0; i<numvals; i++)
        {
            bool found=false;
            SysInt j;
            for(j=0; j < (SysInt)val_array.size(); j++)
            {
                if(val_array[j]==i+dom_min)
                {
                    found=true;
                    break;
                }
            }

            if(!found)
            {
                val_to_cap_index[i]=-1;
            }
            else
            {
                val_to_cap_index[i]=j;
            }
        }
        GCCPRINT("val_to_cap_index:" << val_to_cap_index);

        augpath.reserve(numvars+numvals+1);
        //fifo.reserve(numvars+numvals);

        #ifdef CAPBOUNDSCACHE
        boundsupported.resize(numvals*2, -1);
        // does the bound need to be updated? Indexed as validx*2 for lowerbound, validx*2+1 for ub
        // Contains the capacity value which is supported. Reset to -1 if the support is lost.
        #endif

        for(SysInt i=0; i<numvars; i++)
            varvalmatching[i]=dom_min-1;

        #ifdef QUIMPER
        hopcroft2_setup();
        lbcmatching.resize(numvars, dom_min-1);
        lbcusage.resize(numvals, 0);
        #endif

        #if DomainCounting || InternalDT
        changed_vars_per_scc.resize(numvars+numvals);
        #endif
    }

    CapArray capacity_array;   // capacities for values of interest
    vector<SysInt> val_array;   // values of interest

    vector<SysInt> val_to_cap_index;

    vector<SysInt> vars_in_scc;
    vector<SysInt> vals_in_scc;  // Actual values.

    #if DomainCounting || InternalDT
    vector<vector<SysInt> > changed_vars_per_scc;
    #endif

    #if InternalDT
    InternalDynamicTriggers<VarArray> idt;
    #endif

    virtual void full_propagate()
    {
        for(SysInt i=0; i < (SysInt)capacity_array.size(); i++)
        {
            if(val_array[i]>=dom_min && val_array[i]<=dom_max)
            {
                capacity_array[i].setMin(0);
                capacity_array[i].setMax(numvars);
            }
            else
            {   // value can't occur.
                capacity_array[i].propagateAssign(0);
            }
        }
        #if UseIncGraph
        {
            // update the adjacency lists. and place dts
            DynamicTrigger* dt=dynamic_trigger_start();
            for(SysInt i=dom_min; i<=dom_max; i++)
            {
                for(SysInt j=0; j<adjlistlength[i-dom_min+numvars]; j++)
                {
                    SysInt var=adjlist[i-dom_min+numvars][j];
                    if(!var_array[var].inDomain(i))
                    {
                        if(varvalmatching[var]==i)
                        {
                            usage[varvalmatching[var]-dom_min]--;
                            varvalmatching[var]=dom_min-1;
                        }
                        // swap with the last element and remove
                        adjlist_remove(var, i);
                        j--; // stay in the same place, dont' skip over the
                        // value which was just swapped into the current position.
                    }
                    else
                    {
                        // arranged in blocks for each variable, with numvals triggers in each block
                        DynamicTrigger* mydt= dt+(var*numvals)+(i-dom_min);
                        var_array[var].addDynamicTrigger(mydt, DomainRemoval, i);
                    }
                }
            }
            D_DATA(check_adjlists());
        }
        #endif

        #ifdef CAPBOUNDSCACHE
            DynamicTrigger* dt=dynamic_trigger_start();
            dt+=(numvars*numvals);
            for(SysInt i=0; i < (SysInt)val_array.size(); i++)
            {
                // lowerbound first
                for(SysInt j=0; j<((SysInt)val_array.size()+numvars); j++)
                {
                    dt->trigger_info()=(val_array[i]-dom_min)*2;
                    dt++;
                }
                // upperbound
                for(SysInt j=0; j<((SysInt)val_array.size()+numvars); j++)
                {
                    dt->trigger_info()=(val_array[i]-dom_min)*2+1;
                    dt++;
                }
            }
        #endif

        #ifdef SCC
        SCCSplit.remove(numvars+numvals-1);
        for(SysInt i=0; i<numvars+numvals; i++) to_process.insert(i);  // may need to change.
        do_gcc_prop_scc();
        #else
        do_gcc_prop();
        #endif
    }

    // convert constraint into dynamic.
    SysInt dynamic_trigger_count()
    {
        #if UseIncGraph && !defined(CAPBOUNDSCACHE)
            return numvars*numvals; // one for each var-val pair so we know when it is removed.
        #endif

        #if !UseIncGraph && !defined(CAPBOUNDSCACHE)
            return 0;
        #endif

        #ifdef CAPBOUNDSCACHE
            // first numvars*numvals triggers are not used when UseIncGraph is false
            // one block of numvars+val_Array.size() for each bound.
            return numvars*numvals + 2*val_array.size()*(numvars+val_array.size());
        #endif
    }

    virtual void propagate(DomainInt prop_var_in, DomainDelta)
    {
        SysInt prop_var = checked_cast<SysInt>(prop_var_in);
        D_ASSERT(!UseIncGraph || (prop_var>=numvars && prop_var<numvars+numvals ) );
        if(!to_process.in(prop_var))
        {
            to_process.insert(prop_var);  // inserts the number attached to the trigger. For values this is val-dom_min+numvars
        }

        if(!constraint_locked)
        {
            #ifdef SPECIALQUEUE
            constraint_locked = true;
            getQueue(stateObj).pushSpecialTrigger(this);
            #else
            #ifdef SCC
            do_gcc_prop_scc();
            #else
            do_gcc_prop();
            #endif
            #endif
        }
    }

    virtual void propagate(DynamicTrigger* trig)
    {
        #if defined(CAPBOUNDSCACHE) || UseIncGraph
        DynamicTrigger* dtstart=dynamic_trigger_start();
        #endif

        #ifdef CAPBOUNDSCACHE
        if(trig< dtstart+(numvars*numvals))
        #endif
        {
            // which var/val is this trigger attached to?
            D_ASSERT(UseIncGraph);
            #if UseIncGraph
            SysInt diff=trig-dtstart;
            SysInt var=diff/numvals;
            SysInt validx=diff%numvals;

            if(adjlistpos[validx+numvars][var]<adjlistlength[validx+numvars])
            {
                adjlist_remove(var, validx+dom_min); //validx, adjlistpos[validx][var]);
                if(varvalmatching[var]==validx+dom_min) // remove invalid value in the matching.
                {
                    varvalmatching[var]=dom_min-1;
                    usage[validx]--;
                }
                // trigger the constraint here
                #ifdef ONECALL
                if(!to_process.in(var))
                {
                    to_process.insert(var);  // add the var to the queue to be processed.
                }
                if(!constraint_locked)
                {
                    #ifdef SPECIALQUEUE
                    constraint_locked = true;
                    getQueue(stateObj).pushSpecialTrigger(this);
                    #else
                    #ifdef SCC
                    do_gcc_prop_scc();
                    #else
                    do_gcc_prop();
                    #endif
                    #endif
                }
                #endif
            }
            // else the constraint already processed the deletion so don't trigger it again.
            #endif
        }
        #ifdef CAPBOUNDSCACHE
        else
        {
            D_ASSERT(trig>= dtstart && trig<dtstart+(2*val_array.size()*(numvars+val_array.size())) );
            boundsupported[trig->trigger_info()]=-1;
        }
        #endif

        #if !defined(CAPBOUNDSCACHE) && !UseIncGraph
        D_ASSERT(false)   // Should not have dynamic trigger events!!
        #endif
    }

    #ifdef CAPBOUNDSCACHE
    vector<SysInt> boundsupported;  // does the bound need to be updated? Indexed as validx*2 for lowerbound, validx*2+1 for ub
    // Contains the capacity value which is supported. Reset to -1 if the support is lost.
    #endif

    virtual void special_unlock() { constraint_locked = false; to_process.clear(); }
  virtual void special_check()
  {
    constraint_locked = false;  // should be above the if.

    if(getState(stateObj).isFailed())
    {
        to_process.clear();
        return;
    }
    #ifdef SCC
    do_gcc_prop_scc();
    #else
    do_gcc_prop();
    #endif

  }

  virtual bool get_satisfying_assignment(box<pair<SysInt,DomainInt> >& assignment)
  {
      D_ASSERT(dom_max-dom_min+1 == numvals);
      // Check if the matching is OK.
      bool matchok=true;
      for(SysInt i=0; i<numvars; i++)
      {
          if(!var_array[i].inDomain(varvalmatching[i]))
          {
              matchok=false;
              break;
          }
      }
      if(matchok)
      {
          // now check occurrences
          for(SysInt i=0; i<(SysInt)val_array.size(); i++)
          {
              SysInt val=val_array[i];
              if( (val<dom_min || val>dom_max) && !capacity_array[i].inDomain(0))
              {
                  matchok=false;
                  break;
              }
              if( val>=dom_min && val<=dom_max && !capacity_array[i].inDomain(usage[val-dom_min]))  // is usage OK??
              {
                  matchok=false;
                  break;
              }
          }
      }

      if(!matchok)
      {
          // run matching algorithm
          // populate lower and upper
          // Also check if bounds are well formed.
        for(SysInt i=0; i<(SysInt)val_array.size(); i++)
        {
            if(val_array[i]>=dom_min && val_array[i]<=dom_max)
            {
                if(capacity_array[i].getMax()<0 || capacity_array[i].getMin()>numvars)
                {
                    return false;
                }
                lower[val_array[i]-dom_min]=checked_cast<SysInt>(capacity_array[i].getMin());
                upper[val_array[i]-dom_min]=checked_cast<SysInt>(capacity_array[i].getMax());
            }
            else
            {
                if(capacity_array[i].getMin()>0 || capacity_array[i].getMax()<0)
                {
                    return false;
                }
            }
        }

        #if UseIncGraph
            // update the adjacency lists.
            for(SysInt i=dom_min; i<=dom_max; i++)
            {
                for(SysInt j=0; j<adjlistlength[i-dom_min+numvars]; j++)
                {
                    SysInt var=adjlist[i-dom_min+numvars][j];
                    if(!var_array[var].inDomain(i))
                    {
                        if(varvalmatching[var]==i)
                        {
                            usage[varvalmatching[var]-dom_min]--;
                            varvalmatching[var]=dom_min-1;
                        }
                        // swap with the last element and remove
                        adjlist_remove(var, i);
                        j--; // stay in the same place, dont' skip over the
                        // value which was just swapped into the current position.
                    }
                }
            }
        #endif

        // I'm sure that these four lines are needed, even though
        // if they are taken out, it still passes the random tests.
        // They are needed because the two vectors may be stale after
        // backtracking.
        vars_in_scc.clear();
        for(SysInt i=0; i<numvars; i++) vars_in_scc.push_back(i);
        vals_in_scc.clear();
        for(SysInt i=dom_min; i<=dom_max; i++) vals_in_scc.push_back(i);

        matchok=bfsmatching_gcc();
      }

      if(!matchok)
      {
          return false;
      }
      else
      {
          for(SysInt i=0; i<numvars; i++)
          {
              D_ASSERT(var_array[i].inDomain(varvalmatching[i]));
              assignment.push_back(make_pair(i, varvalmatching[i]));
          }
          for(SysInt i=0; i<(SysInt)val_array.size(); i++)
          {
              SysInt occ;
              if(val_array[i]<dom_min || val_array[i]>dom_max)
              {
                  occ=0;
              }
              else
              {
                  occ=usage[val_array[i]-dom_min];
              }

             if(capacity_array[i].inDomain(occ))
             {
                 assignment.push_back(make_pair(i+numvars, occ));
             }
             else
             {
                 // push upper and lower bounds.
                 assignment.push_back(make_pair(i+numvars, capacity_array[i].getMin()));
                 assignment.push_back(make_pair(i+numvars, capacity_array[i].getMax()));
             }
          }
          return true;
      }
  }

    void do_gcc_prop()
    {
        if(Strongcards)
        {
            PROP_INFO_ADDONE(GCC);
        }
        else
        {
            PROP_INFO_ADDONE(GCCWeak);
        }

        D_DATA(check_adjlists());

        #ifdef QUIMPER
        do_gcc_prop_quimper();
        return;
        #endif

        // find/ repair the matching.
        #ifndef INCREMENTALMATCH
        varvalmatching.clear();
        varvalmatching.resize(numvars, dom_min-1);
        usage.clear();
        usage.resize(numvals, 0);
        #endif

        // populate lower and upper
        for(SysInt i=0; i<(SysInt)val_array.size(); i++)
        {
            if(val_array[i]>=dom_min && val_array[i]<=dom_max)
            {
                lower[val_array[i]-dom_min]=capacity_array[i].getMin();   // doesn't work with duplicate values in list.
                upper[val_array[i]-dom_min]=capacity_array[i].getMax();
            }
        }

        GCCPRINT("lower:"<<lower);
        GCCPRINT("upper:"<<upper);

        bool run_propagator=true;
        #if InternalDT
            run_propagator=false;
            for(SysInt var=0; var<numvars && !run_propagator; var++)
            {
                run_propagator=idt.doesItTrigger(var);
            }
        #endif

        if(run_propagator || Strongcards)
        {
            bool flag=bfsmatching_gcc();
            GCCPRINT("matching:"<<flag);

            if(!flag)
            {
                getState(stateObj).setFailed(true);
                return;
            }
        }

        if(run_propagator)
        {
            tarjan_recursive(0, upper, lower, varvalmatching, usage);
        }
        else
        {
            GCCPRINT("Saved a call with InternalDT.");
        }

        prop_capacity();
    }

    vector<SysInt> lbcmatching;
    vector<SysInt> lbcusage;

    void do_gcc_prop_quimper()
    {
        // find/ repair the matching.
        #ifndef INCREMENTALMATCH
        varvalmatching.clear();
        varvalmatching.resize(numvars, dom_min-1);
        lbcmatching.clear();
        lbcmatching.resize(numvars, dom_min-1);
        usage.clear();
        usage.resize(numvals, 0);
        lbcusage.clear();
        lbcusage.resize(numvals, 0);
        #endif

        // populate lower and upper
        for(SysInt i=0; i<(SysInt)val_array.size(); i++)
        {
            if(val_array[i]>=dom_min && val_array[i]<=dom_max)
            {
                lower[val_array[i]-dom_min]=capacity_array[i].getMin();   // not quite right in the presence of duplicate values.
                upper[val_array[i]-dom_min]=capacity_array[i].getMax();
            }
        }
        GCCPRINT("lower:"<<lower);
        GCCPRINT("upper:"<<upper);

        // first process the lower bound constraint.
        // use lower for the 'upper' parameter here.
        hopcroft2(vars_in_scc, lbcmatching, lower, lbcusage);

        for(SysInt i=0; i<numvals; i++)
        {
            if(lbcusage[i]<lower[i])
            {
                // can't hit the lower bound.
                GCCPRINT("failing because can't construct lower bound matching.");
                getState(stateObj).setFailed(true);
                return;
            }
        }

        GCCPRINT("Unpadded lbc matching: " << lbcmatching);

        // fill in the blanks in the matching
        for(SysInt i=0; i<numvars; i++)
        {
            if(lbcmatching[i]==dom_min-1)
            {
                DomainInt minval=var_array[i].getMin();
                lbcmatching[i]=minval;
                lbcusage[minval-dom_min]++;
            }
        }

        GCCPRINT("Padded lbc matching: " << lbcmatching);

        // args are upper, lower, matching
        // use interval [lower .. numvars]
        // use augpath temporarily for the upper bound.
        augpath.clear();
        augpath.resize(numvals, numvars);
        tarjan_recursive(0, augpath, lower, lbcmatching, lbcusage);

        // Now ubc

        if(!hopcroft_wrapper2(vars_in_scc, varvalmatching, upper, usage))
        {
            GCCPRINT("failed when constructing ubc matching.");
            return;
        }
        GCCPRINT("ubc matching: " << varvalmatching);

        // borrow augpath for the lower bounds.
        augpath.clear();
        augpath.resize(numvals, 0);
        tarjan_recursive(0, upper, augpath, varvalmatching, usage);

        prop_capacity();
    }

    smallset sccs_to_process;

    void do_gcc_prop_scc()
    {
        if(Strongcards)
        {
            PROP_INFO_ADDONE(GCC);
        }
        else
        {
            PROP_INFO_ADDONE(GCCWeak);
        }

        D_DATA(check_adjlists());

        // Assumes triggered on variables in to_process
        #ifndef INCREMENTALMATCH
        varvalmatching.clear();
        varvalmatching.resize(numvars, dom_min-1);
        usage.clear();
        usage.resize(numvals, 0);
        #endif

        sccs_to_process.clear();
        {
        vector<SysInt>& toiterate = to_process.getlist();
        GCCPRINT("About to loop for to_process.");

        // to_process contains var indexes and vals:
        // (val-dom_min +numvars)

        for(SysInt i=0; i<(SysInt)toiterate.size(); ++i)
        {
            SysInt tempidx=toiterate[i];

            SysInt sccindex_start=varToSCCIndex[tempidx];

            while(sccindex_start>0 && SCCSplit.isMember(sccindex_start-1))
            {
                sccindex_start--;   // seek the first item in the SCC.
            }

            if(!sccs_to_process.in(sccindex_start)
                && SCCSplit.isMember(sccindex_start))   // not singleton.
            {
                sccs_to_process.insert(sccindex_start);
                #if DomainCounting || InternalDT
                    changed_vars_per_scc[sccindex_start].clear();
                #endif
            }
            #if DomainCounting || InternalDT
                // make a note of which changed vars are in the scc.
                if(SCCSplit.isMember(sccindex_start))   // if not singleton..??
                    changed_vars_per_scc[sccindex_start].push_back(tempidx);
            #endif
        }
        }
        to_process.clear();
        {
        vector<SysInt>& toiterate = sccs_to_process.getlist();
        GCCPRINT("About to loop for sccs_to_process:"<< toiterate);
        for(SysInt i=0; i<(SysInt)toiterate.size(); i++)
        {
            SysInt sccindex_start=toiterate[i];
            vars_in_scc.clear();
            vals_in_scc.clear();
            for(SysInt j=sccindex_start; j<(numvars+numvals); j++)
            {
                // copy vars and vals in the scc into two vectors.
                SysInt sccval=SCCs[j];
                if(sccval<numvars)
                {
                    D_ASSERT(sccval>=0);
                    vars_in_scc.push_back(sccval);
                }
                else
                {
                    D_ASSERT(sccval<numvars+numvals);
                    vals_in_scc.push_back(sccval-numvars+dom_min);
                }

                if(!SCCSplit.isMember(j)) break;
            }
            GCCPRINT("vars_in_scc:" << vars_in_scc);
            GCCPRINT("vals_in_scc:" << vals_in_scc);

            // Might not need to do anything.
            if(vars_in_scc.size()==0)
            {
                GCCPRINT("refusing to process scc with no vars.");
                continue;
            }

            // populate lower and upper
            for(SysInt i=0; i<(SysInt)vals_in_scc.size(); i++)
            {
                SysInt validx=vals_in_scc[i]-dom_min;
                SysInt capi=val_to_cap_index[validx];
                if(capi>-1)
                {
                    lower[validx]=checked_cast<SysInt>(capacity_array[capi].getMin());
                    upper[validx]=checked_cast<SysInt>(capacity_array[capi].getMax());
                }
            }

            // A flag that indicates whether we do Tarjan's and possibly FF
            bool run_propagator=true;
            #if DomainCounting || InternalDT // need to check through triggers to see if broken....
            {
                run_propagator=false;
                vector<SysInt>& vars_changed=changed_vars_per_scc[sccindex_start];
                #if DomainCounting
                    SysInt varcount=vars_in_scc.size();
                    for(SysInt i=0; i<(SysInt)vars_changed.size(); i++)
                    {
                        SysInt var=vars_changed[i];
                        if(var>=numvars || adjlistlength[var]<varcount)
                        {   // either its a val (i.e. a trigger from a cap variable)
                            // or its a var with fewer than all the values.
                            run_propagator=true;
                            break;
                        }
                    }
                #endif
                #if InternalDT
                    for(SysInt i=0; i<(SysInt)vars_changed.size() && !run_propagator; i++)
                    {
                        SysInt var=vars_changed[i];
                        if(var>=numvars)
                        {
                            // var is actually a val. Triggered from a
                            // capacity variable.
                            run_propagator=true;
                            break;
                        }
                        run_propagator=idt.doesItTrigger(var);
                    }
                #endif
            }
            #endif

            if(run_propagator || Strongcards)
            {
                bool flag=bfsmatching_gcc();
                if(!flag)
                {
                    GCCPRINT("Failing because no matching");
                    getState(stateObj).setFailed(true);
                    return;
                }
            }

            if(run_propagator)
            {
                //cout << 1 << endl;
                tarjan_recursive(sccindex_start, upper, lower, varvalmatching, usage);
            }
            else
            {
                //cout << "Saved a call to tarjan's with dc/wl" << endl;
            }

            #ifdef SCCCARDS
                // Propagate to capacity variables for all values in vals_in_scc
                for(SysInt valinscc=0; valinscc<(SysInt)vals_in_scc.size(); valinscc++)
                {
                    SysInt v=vals_in_scc[valinscc];
                    if(val_to_cap_index[v-dom_min]!=-1 && lower[v-dom_min]!=upper[v-dom_min])
                    {
                        if(Strongcards)
                        {
                            prop_capacity_strong_scc(v);
                        }
                        else
                        {
                            prop_capacity_simple(v);
                        }
                    }
                }
            #endif
        }
        }

        #ifndef SCCCARDS
        prop_capacity();
        #endif
    }

    deque<SysInt> fifo;
    // deque_fixed_size was not faster.
    //deque_fixed_size fifo;
    vector<SysInt> prev;

    vector<SysInt> matchbac;

    vector<SysInt> lower;
    vector<SysInt> upper;
    vector<SysInt> usage;
    vector<SysInt> usagebac;

    // Incremental SCC data.
    vector<SysInt> SCCs;    // Variable numbers and values as val-dom_min+numvars
    ReversibleMonotonicSet SCCSplit;

    vector<SysInt> varToSCCIndex;  // Mirror of the SCCs array.

    smallset to_process;

    inline bool bfsmatching_gcc()
    {
        // lower and upper are indexed by value-dom_min and provide the capacities.
        // usage is the number of times a value is used in the matching.

        // current sccs are contained in vars_in_scc and vals_in_scc

        // back up the matching to cover failure
        //matchbac=varvalmatching;
        //usagebac=usage;

        // clear out unmatched variables -- unless this has already been done
        // when the adjacency lists were updated.
        #if !UseIncGraph
        for(SysInt scci=0; scci<(SysInt)vars_in_scc.size(); scci++)
        {
            SysInt i=vars_in_scc[scci];
            if(varvalmatching[i]!=dom_min-1
                && !var_array[i].inDomain(varvalmatching[i]))
            {
                usage[varvalmatching[i]-dom_min]--;
                varvalmatching[i]=dom_min-1;   // marker for unmatched.
            }
        }
        #endif

        // If the upper bounds have been changed since last call, it is possible that
        // the usage[val] of some value is greater than upper[val]. This is impossible
        // in the flow graph, so it must be corrected before we run the algorithm.
        // Some values in the matching are changed to blank (dom_min-1).
        for(SysInt valsccindex=0; valsccindex<(SysInt)vals_in_scc.size(); valsccindex++)
        {
            SysInt valindex=vals_in_scc[valsccindex]-dom_min;
            if(usage[valindex]>upper[valindex] && upper[valindex]>=0)
            {
                for(SysInt i=0; i<(SysInt)vars_in_scc.size() && usage[valindex]>upper[valindex]; i++)
                {
                    SysInt j=vars_in_scc[i];
                    if(varvalmatching[j]==valindex+dom_min)
                    {
                        varvalmatching[j]=dom_min-1;
                        usage[valindex]--;
                    }
                }
                D_ASSERT(usage[valindex]==upper[valindex]);
            }
        }

        // iterate through the values looking for ones which are below their lower capacity bound.
        for(SysInt startvalsccindex=0; startvalsccindex<(SysInt)vals_in_scc.size(); startvalsccindex++)
        {
            SysInt startvalindex=vals_in_scc[startvalsccindex]-dom_min;
            while(usage[startvalindex]<lower[startvalindex])
            {
                // usage of val needs to increase. Construct an augmenting path starting at val.
                GCCPRINT("Searching for augmenting path for val: " << startvalindex+dom_min);
                // Matching edge lost; BFS search for augmenting path to fix it.
                fifo.clear();  // this should be constant time but probably is not.
                fifo.push_back(startvalindex+numvars);
                visited.clear();
                visited.insert(startvalindex+numvars);
                bool finished=false;
                while(!fifo.empty() && !finished)
                {
                    // pop a vertex and expand it.
                    SysInt curnode=fifo.front();
                    fifo.pop_front();
                    GCCPRINT("Popped vertex " << (curnode<numvars? "(var)":"(val)") << (curnode<numvars? curnode : curnode+dom_min-numvars ));
                    if(curnode<numvars)
                    { // it's a variable
                        // follow the matching edge, if there is one.
                        SysInt valtoqueue=varvalmatching[curnode];
                        if(valtoqueue!=dom_min-1
                            && !visited.in(valtoqueue-dom_min+numvars))
                        {
                            D_ASSERT(var_array[curnode].inDomain(valtoqueue));
                            SysInt validx=valtoqueue-dom_min+numvars;
                            if(usage[valtoqueue-dom_min]>lower[valtoqueue-dom_min])
                            {
                                // can reduce the flow of valtoqueue to increase startval.
                                // This is a circular path back to S.
                                prev[validx]=curnode;
                                apply_augmenting_path(validx, startvalindex+numvars);
                                finished=true;
                            }
                            else
                            {
                                visited.insert(validx);
                                prev[validx]=curnode;
                                fifo.push_back(validx);
                            }
                        }
                    }
                    else
                    { // popped a value from the stack.
                        D_ASSERT(curnode>=numvars && curnode < numvars+numvals);
                        SysInt stackval=curnode+dom_min-numvars;
                        #if !UseIncGraph
                        for(SysInt vartoqueuescc=0; vartoqueuescc<(SysInt)vars_in_scc.size(); vartoqueuescc++)
                        {
                            SysInt vartoqueue=vars_in_scc[vartoqueuescc];
                        #else
                        for(SysInt vartoqueuei=0; vartoqueuei<adjlistlength[stackval-dom_min+numvars]; vartoqueuei++)
                        {
                            SysInt vartoqueue=adjlist[stackval-dom_min+numvars][vartoqueuei];
                        #endif
                            // For each variable, check if it terminates an odd alternating path
                            // and also queue it if it is suitable.
                            if(!visited.in(vartoqueue)
                                #if !UseIncGraph
                                && var_array[vartoqueue].inDomain(stackval)
                                #endif
                                && varvalmatching[vartoqueue]!=stackval)   // Need to exclude the matching edges????
                            {
                                // there is an edge from stackval to vartoqueue.
                                if(varvalmatching[vartoqueue]==dom_min-1)
                                {
                                    // vartoqueue terminates an odd alternating path.
                                    // Unwind and apply the path here
                                    prev[vartoqueue]=curnode;
                                    apply_augmenting_path(vartoqueue, startvalindex+numvars);
                                    finished=true;
                                    break;  // get out of for loop
                                }
                                else
                                {
                                    // queue vartoqueue
                                    visited.insert(vartoqueue);
                                    prev[vartoqueue]=curnode;
                                    fifo.push_back(vartoqueue);
                                }
                            }
                        }  // end for.
                    }  // end value
                }  // end while
                if(!finished)
                {   // no augmenting path found
                    GCCPRINT("No augmenting path found.");
                    // restore the matching to its state before the algo was called.
                    //varvalmatching=matchbac;
                    //usage=usagebac;
                    return false;
                }

            }  // end while below lower bound.
        } // end for each value

        // now search for augmenting paths for unmatched vars.

        GCCPRINT("feasible matching (respects lower & upper bounds):"<<varvalmatching);

        if(UseTranspose)
        {
            // Flip the graph around, so it's like the alldiff case now.
            // follow an edge in the matching from a value to a variable,
            // follow edges not in the matching from variables to values.
            for(SysInt startvarscc=0; startvarscc<(SysInt)vars_in_scc.size(); startvarscc++)
            {
                SysInt startvar=vars_in_scc[startvarscc];
                if(varvalmatching[startvar]==dom_min-1)
                {
                    GCCPRINT("Searching for augmenting path for var: " << startvar);
                    fifo.clear();  // this should be constant time but probably is not.
                    fifo.push_back(startvar);
                    visited.clear();
                    visited.insert(startvar);
                    bool finished=false;
                    while(!fifo.empty() && !finished)
                    {
                        // pop a vertex and expand it.
                        SysInt curnode=fifo.front();
                        fifo.pop_front();
                        GCCPRINT("Popped vertex " << (curnode<numvars? "(var)":"(val)") << (curnode<numvars? curnode : curnode+dom_min-numvars ));
                        if(curnode<numvars)
                        { // it's a variable
                            // follow all edges other than the matching edge.
                            #if !UseIncGraph
                            for(DomainInt valtoqueue=var_array[curnode].getMin(); valtoqueue<=var_array[curnode].getMax(); valtoqueue++)
                            {
                            #else
                            for(SysInt valtoqueuei=0; valtoqueuei<adjlistlength[curnode]; valtoqueuei++)
                            {
                                SysInt valtoqueue=adjlist[curnode][valtoqueuei];
                            #endif
                                // For each value, check if it terminates an odd alternating path
                                // and also queue it if it is suitable.
                                SysInt validx=valtoqueue-dom_min+numvars;
                                if(valtoqueue!=varvalmatching[curnode]
                                #if !UseIncGraph
                                    && var_array[curnode].inDomain(valtoqueue)
                                #endif
                                    && !visited.in(validx) )
                                {
                                    //D_ASSERT(find(vals_in_scc.begin(), vals_in_scc.end(), valtoqueue)!=vals_in_scc.end()); // the value is in the scc.
                                    // Does this terminate an augmenting path?
                                    if(usage[valtoqueue-dom_min]<upper[valtoqueue-dom_min])
                                    {
                                        // valtoqueue terminates an alternating path.
                                        // Unwind and apply the path here
                                        prev[validx]=curnode;
                                        apply_augmenting_path_reverse(validx, startvar);
                                        finished=true;
                                        break;  // get out of for loop
                                    }
                                    else
                                    {
                                        // queue valtoqueue
                                        visited.insert(validx);
                                        prev[validx]=curnode;
                                        fifo.push_back(validx);
                                    }
                                }
                            }  // end for.
                        }
                        else
                        { // popped a value from the stack.
                            D_ASSERT(curnode>=numvars && curnode < numvars+numvals);
                            SysInt stackval=curnode+dom_min-numvars;
                            #if !UseIncGraph
                            for(SysInt vartoqueuescc=0; vartoqueuescc<(SysInt)vars_in_scc.size(); vartoqueuescc++)
                            {
                                SysInt vartoqueue=vars_in_scc[vartoqueuescc];
                            #else
                            for(SysInt vartoqueuei=0; vartoqueuei<adjlistlength[curnode]; vartoqueuei++)
                            {
                                SysInt vartoqueue=adjlist[curnode][vartoqueuei];
                            #endif
                                // For each variable which is matched to stackval, queue it.
                                if(!visited.in(vartoqueue)
                                    && varvalmatching[vartoqueue]==stackval)
                                {
                                    D_ASSERT(var_array[vartoqueue].inDomain(stackval));
                                    // there is an edge from stackval to vartoqueue.
                                    // queue vartoqueue
                                    visited.insert(vartoqueue);
                                    prev[vartoqueue]=curnode;
                                    fifo.push_back(vartoqueue);
                                }
                            }  // end for.
                        }  // end value
                    }  // end while
                    if(!finished)
                    {   // no augmenting path found
                        GCCPRINT("No augmenting path found.");
                        // restore the matching to its state before the algo was called.
                        //varvalmatching=matchbac;   // no need for this.
                        //usage=usagebac;
                        return false;
                    }
                }
            }
            GCCPRINT("maximum matching:" << varvalmatching);
            return true;
        }
        else
        {   // Do not use the transpose graph.
            // Graph is oriented s -> values -> variables -> t
            // Follow a matching edge from variable to value.
            // Follow edges not in the matching from values to variables.
            // iterate through the values looking for ones which are below their lower capacity bound.

            // First count unmatched.
            SysInt unmatched=0;
            for(SysInt varidx=0; varidx<(SysInt)vars_in_scc.size(); varidx++)
            {
                if(varvalmatching[vars_in_scc[varidx]]==dom_min-1)
                    unmatched++;
            }
            if(unmatched==0)
                return true;  // matching already complete.
            for(SysInt startvalsccindex=0; startvalsccindex<(SysInt)vals_in_scc.size(); startvalsccindex++)
            {
                SysInt startvalindex=vals_in_scc[startvalsccindex]-dom_min;
                while(usage[startvalindex]<upper[startvalindex])
                {
                    // usage of val may increase. Construct an augmenting path starting at val.
                    GCCPRINT("Searching for augmenting path for val: " << startvalindex+dom_min);
                    // Matching edge lost; BFS search for augmenting path to fix it.
                    fifo.clear();  // this should be constant time but probably is not.
                    fifo.push_back(startvalindex+numvars);
                    visited.clear();
                    visited.insert(startvalindex+numvars);
                    bool finished=false;
                    while(!fifo.empty() && !finished)
                    {
                        // pop a vertex and expand it.
                        SysInt curnode=fifo.front();
                        fifo.pop_front();
                        GCCPRINT("Popped vertex " << (curnode<numvars? "(var)":"(val)") << (curnode<numvars? curnode : curnode+dom_min-numvars ));
                        if(curnode<numvars)
                        { // it's a variable
                            // follow the matching edge, if there is one.
                            SysInt valtoqueue=varvalmatching[curnode];
                            if(valtoqueue!=dom_min-1
                                && !visited.in(valtoqueue-dom_min+numvars))
                            {
                                D_ASSERT(var_array[curnode].inDomain(valtoqueue));
                                SysInt validx=valtoqueue-dom_min+numvars;
                                visited.insert(validx);
                                prev[validx]=curnode;
                                fifo.push_back(validx);
                            }
                        }
                        else
                        { // popped a value from the stack.
                            D_ASSERT(curnode>=numvars && curnode < numvars+numvals);
                            SysInt stackval=curnode+dom_min-numvars;
                            #if !UseIncGraph
                            for(SysInt vartoqueuescc=0; vartoqueuescc<(SysInt)vars_in_scc.size(); vartoqueuescc++)
                            {
                                SysInt vartoqueue=vars_in_scc[vartoqueuescc];
                            #else
                            for(SysInt vartoqueuei=0; vartoqueuei<adjlistlength[stackval-dom_min+numvars]; vartoqueuei++)
                            {
                                SysInt vartoqueue=adjlist[stackval-dom_min+numvars][vartoqueuei];
                            #endif
                                // For each variable, check if it terminates an odd alternating path
                                // and also queue it if it is suitable.
                                if(!visited.in(vartoqueue)
                                    #if !UseIncGraph
                                    && var_array[vartoqueue].inDomain(stackval)
                                    #endif
                                    && varvalmatching[vartoqueue]!=stackval)   // Need to exclude the matching edges????
                                {
                                    // there is an edge from stackval to vartoqueue.
                                    if(varvalmatching[vartoqueue]==dom_min-1)
                                    {
                                        // vartoqueue terminates an odd alternating path.
                                        // Unwind and apply the path here
                                        prev[vartoqueue]=curnode;
                                        apply_augmenting_path(vartoqueue, startvalindex+numvars);
                                        finished=true;
                                        unmatched--;
                                        if(unmatched==0)
                                            return true;   // The matching has been completed
                                        break;  // get out of for loop
                                    }
                                    else
                                    {
                                        // queue vartoqueue
                                        visited.insert(vartoqueue);
                                        prev[vartoqueue]=curnode;
                                        fifo.push_back(vartoqueue);
                                    }
                                }
                            }  // end for.
                        }  // end value
                    }  // end while
                    if(!finished)
                    {
                        // No augmenting path found for startval, so jump out of the loop
                        break;
                    }
                }  // end while below upper bound.
            } // end for each value
            D_ASSERT(unmatched>0);
            return false;  // If we got to here, we have iterated through
            // all values and not completed the matching.
        } // End of !UseTranspose
    }

    inline void apply_augmenting_path(SysInt unwindnode, SysInt startnode)
    {
        augpath.clear();
        // starting at unwindnode, unwind the path and put it in augpath.
        // Then apply it.
        // Assumes prev contains vertex numbers, rather than vars and values.
        SysInt curnode=unwindnode;
        while(curnode!=startnode)
        {
            augpath.push_back(curnode);
            curnode=prev[curnode];
        }
        augpath.push_back(curnode);

        std::reverse(augpath.begin(), augpath.end());

        GCCPRINT("Found augmenting path:" << augpath);

        // now apply the path.
        for(SysInt i=0; i<(SysInt)augpath.size()-1; i++)
        {
            if(augpath[i]<numvars)
            {
                // if it's a variable
                //D_ASSERT(varvalmatching[augpath[i]]==dom_min-1);
                // varvalmatching[augpath[i]]=dom_min-1; Can't do this, it would overwrite the correct value.
                GCCPRINT("decrementing usage for value " << augpath[i+1]-numvars+dom_min);
                usage[augpath[i+1]-numvars]--;
            }
            else
            {   // it's a value.
                D_ASSERT(augpath[i]>=numvars && augpath[i]<numvars+numvals);
                varvalmatching[augpath[i+1]]=augpath[i]-numvars+dom_min;
                GCCPRINT("incrementing usage for value " << augpath[i]-numvars+dom_min);
                usage[augpath[i]-numvars]++;
            }
        }

        GCCPRINT("varvalmatching: "<<varvalmatching);
    }

    inline void apply_augmenting_path_reverse(SysInt unwindnode, SysInt startnode)
    {
        augpath.clear();
        // starting at unwindnode, unwind the path and put it in augpath.
        // Then apply it.
        // Assumes prev contains vertex numbers, rather than vars and values.
        SysInt curnode=unwindnode;
        while(curnode!=startnode)
        {
            augpath.push_back(curnode);
            curnode=prev[curnode];
        }
        augpath.push_back(curnode);

        std::reverse(augpath.begin(), augpath.end());
        GCCPRINT("Found augmenting path:" << augpath);

        // now apply the path.
        for(SysInt i=0; i<(SysInt)augpath.size()-1; i++)
        {
            if(augpath[i]<numvars)
            {
                // if it's a variable
                D_ASSERT(varvalmatching[augpath[i]]==dom_min-1);
                varvalmatching[augpath[i]]=augpath[i+1]-numvars+dom_min;
                usage[augpath[i+1]-numvars]++;
            }
            else
            {   // it's a value.
                D_ASSERT(augpath[i]>=numvars && augpath[i]<numvars+numvals);
                varvalmatching[augpath[i+1]]=dom_min-1;
                usage[augpath[i]-numvars]--;
            }
        }

        GCCPRINT("varvalmatching:" << varvalmatching);
    }

    virtual string constraint_name()
    {
        if(Strongcards)
            return "gcc";
        else
            return "gccweak";
    }

    CONSTRAINT_ARG_LIST3(var_array, val_array, capacity_array);

    virtual triggerCollection setup_internal()
    {
        triggerCollection t;
        SysInt capacity_size=capacity_array.size();

        #if !UseIncGraph || !defined(ONECALL)
            SysInt array_size = var_array.size();
            for(SysInt i = 0; i < array_size; ++i)
            {
                t.push_back(make_trigger(var_array[i], Trigger(this, i), DomainChanged));
            }
        #endif

        for(SysInt i=0; i< capacity_size; ++i)
        {
            if(val_array[i]>=dom_min && val_array[i]<=dom_max)
            {
                t.push_back(make_trigger(capacity_array[i], Trigger(this, val_array[i]-dom_min + numvars), UpperBound));
                t.push_back(make_trigger(capacity_array[i], Trigger(this, val_array[i]-dom_min + numvars), LowerBound));
            }
        }
        return t;
    }

    virtual vector<AnyVarRef> get_vars()
    {
      vector<AnyVarRef> vars;
      vars.reserve(var_array.size());
      for(UnsignedSysInt i = 0; i < var_array.size(); ++i)
        vars.push_back(var_array[i]);
      for(UnsignedSysInt i = 0; i < capacity_array.size(); ++i)
        vars.push_back(capacity_array[i]);
      return vars;
    }

    virtual BOOL check_assignment(DomainInt* v, SysInt vsize)
    {
      D_ASSERT(vsize == (SysInt)var_array.size()+(SysInt)capacity_array.size());
      // borrow augpath array
      GCCPRINT("In check_assignment with array:[");
      for(SysInt i=0; i<vsize; i++)
      { GCCPRINT( v[i] <<","); }
      GCCPRINT("]");
      augpath.clear();
      augpath.resize(numvals, 0);

      for(SysInt i=0; i<numvars; i++)
      {   // count the values.
          augpath[checked_cast<SysInt>(v[i]-dom_min)]++;
      }
      for(SysInt i=0; i<(SysInt)val_array.size(); i++)
      {
          SysInt val=val_array[i];
          if(val>=dom_min && val<=dom_max)
          {
              if(v[i+numvars]!=augpath[val-dom_min])
              {
                  return false;
              }
          }
          else
          {
              if(v[i+numvars]!=0)
              {
                  return false;
              }
          }
      }
      return true;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Tarjan's algorithm


    vector<SysInt> tstack;
    smallset_nolist in_tstack;
    smallset_nolist visited;
    vector<SysInt> dfsnum;
    vector<SysInt> lowlink;

    vector<SysInt> curnodestack;

    bool scc_split;

    SysInt sccindex;

    SysInt max_dfs;

    SysInt varcount, valcount;
    //SysInt localmin,localmax;

    void initialize_tarjan()
    {
        SysInt numnodes=numvars+numvals+1;  // One sink node.
        tstack.reserve(numnodes);
        in_tstack.reserve(numnodes);
        visited.reserve(numnodes);
        max_dfs=1;
        scc_split=false;
        dfsnum.resize(numnodes);
        lowlink.resize(numnodes);

        //iterationstack.resize(numnodes);
        curnodestack.reserve(numnodes);

        //valinlocalmatching.reserve(numvals);
        //varinlocalmatching.reserve(numvars);
    }

    void tarjan_recursive(SysInt sccindex_start,
        vector<SysInt>& upper,
        vector<SysInt>& lower, vector<SysInt>& matching, vector<SysInt>& usage)
    {
        tstack.clear();
        in_tstack.clear();

        visited.clear();
        max_dfs=1;

        scc_split=false;
        sccindex=sccindex_start;

        #if InternalDT
            for(SysInt i=0; i<(SysInt)vars_in_scc.size(); i++)
            {
                SysInt varidx=vars_in_scc[i];
                idt.clearwatches(varidx);
                idt.addwatch(varidx, matching[varidx]);
                GCCPRINT("Adding DT for var " << varidx << " val " << matching[varidx]);
            }
        #endif

        for(SysInt i=0; i<(SysInt)vars_in_scc.size(); ++i)
        {
            SysInt curnode=vars_in_scc[i];
            if(!visited.in(curnode))
            {
                GCCPRINT("(Re)starting tarjan's algorithm, at node:"<< curnode);
                varcount=0; valcount=0;
                visit(curnode, true, upper, lower, matching, usage);
                GCCPRINT("Returned from tarjan's algorithm.");
            }
        }

        // Also make sure all vals have been visited, so that values which
        // are in singleton SCCs are removed from all vars.
        for(SysInt i=0; i<(SysInt)vals_in_scc.size(); ++i)
        {
            SysInt curnode=vals_in_scc[i]-dom_min+numvars;
            if(!visited.in(curnode))
            {
                GCCPRINT("(Re)starting tarjan's algorithm, at node:"<< curnode);
                varcount=0; valcount=0;
                visit(curnode, true, upper, lower, matching, usage);
                GCCPRINT("Returned from tarjan's algorithm.");
            }
        }

        #if RemoveAssignedVars
            // Didn't split SCCs in visit function, do it here by just taking
            // out assigned vars.
            for(SysInt i=0; i<numvars+numvals; i++)
            {
                D_ASSERT(std::find(SCCs.begin(), SCCs.end(), i)!=SCCs.end() );
            }
            for(SysInt i=sccindex_start; i<numvars+numvals; i++)
            {
                if(SCCs[i]<numvars && var_array[SCCs[i]].isAssigned())
                {
                    // swap with first element
                    if(sccindex_start!=i)
                    {
                        SysInt temp=SCCs[sccindex_start];
                        SCCs[sccindex_start]=SCCs[i];
                        SCCs[i]=temp;
                        varToSCCIndex[SCCs[i]]=i;
                        varToSCCIndex[SCCs[sccindex_start]]=sccindex_start;
                    }
                    // partition
                    D_ASSERT(SCCSplit.isMember(sccindex_start));
                    SCCSplit.remove(sccindex_start);
                    sccindex_start++;
                }
                if(!SCCSplit.isMember(i))
                {
                    break;
                }
            }
            for(SysInt i=0; i<numvars+numvals; i++)
            {
                D_ASSERT(std::find(SCCs.begin(), SCCs.end(), i)!=SCCs.end() );
            }

        #endif
    }

    void visit(SysInt curnode, bool toplevel, vector<SysInt>& upper, vector<SysInt>& lower, vector<SysInt>& matching, vector<SysInt>& usage)
    {
        // toplevel is true iff this is the top level of the recursion.
        tstack.push_back(curnode);
        in_tstack.insert(curnode);
        dfsnum[curnode]=max_dfs;
        lowlink[curnode]=max_dfs;
        max_dfs++;
        visited.insert(curnode);
        GCCPRINT("Visiting node: " <<curnode);

        if(curnode==numvars+numvals)
        {
            //cout << "Visiting sink node." <<endl;
            // It's the sink so it links to all spare values.
            /*
            for(SysInt i=0; i<spare_values.size(); ++i)
            {
                SysInt newnode=spare_values[i];
                //cout << "About to visit spare value: " << newnode-numvars+dom_min <<endl;
                if(!visited.in(newnode))
                {
                    visit(newnode);
                    if(lowlink[newnode]<lowlink[curnode])
                    {
                        lowlink[curnode]=lowlink[newnode];
                    }
                }
                else
                {
                    // Already visited newnode
                    if(in_tstack.in(newnode) && dfsnum[newnode]<lowlink[curnode])
                    {
                        lowlink[curnode]=dfsnum[newnode];
                    }
                }
            }*/

            // GCC mod:
            // link to any value which is below its upper cap.
            GCCPRINT("usage:"<<usage);
            GCCPRINT("upper:"<<upper);

            for(SysInt j=0; j<(SysInt)vals_in_scc.size(); j++)
            {
                SysInt i=vals_in_scc[j];
                SysInt newnode=i+numvars-dom_min;
                if(usage[i-dom_min]<upper[i-dom_min])
                {
                    GCCPRINT("val "<< i << "below upper cap.");
                    if(!visited.in(newnode))
                    {
                        visit(newnode, false, upper, lower, matching, usage);
                        if(lowlink[newnode]<lowlink[curnode])
                        {
                            lowlink[curnode]=lowlink[newnode];
                        }
                    }
                    else
                    {
                        // Already visited newnode
                        if(in_tstack.in(newnode) && dfsnum[newnode]<lowlink[curnode])
                        {
                            lowlink[curnode]=dfsnum[newnode];
                        }
                    }
                }
            }
        }
        else if(curnode<numvars)
        {
            D_ASSERT(find(vars_in_scc.begin(), vars_in_scc.end(), curnode)!=vars_in_scc.end());
            varcount++;
            SysInt newnode=matching[curnode]-dom_min+numvars;
            //D_ASSERT(var_array[curnode].inDomain(matching[curnode]));
            D_ASSERT(adjlistpos[curnode][matching[curnode]-dom_min]<adjlistlength[curnode]);

            if(!visited.in(newnode))
            {
                visit(newnode, false, upper, lower, matching, usage);
                if(lowlink[newnode]<lowlink[curnode])
                {
                    lowlink[curnode]=lowlink[newnode];
                }
            }
            else
            {
                // Already visited newnode
                if(in_tstack.in(newnode) && dfsnum[newnode]<lowlink[curnode])
                {
                    lowlink[curnode]=dfsnum[newnode];  // Why dfsnum not lowlink?
                }
            }
        }
        else
        {
            // curnode is a value
            // This is the only case where watches are set.
            //cout << "Visiting node val: "<< curnode+dom_min-numvars <<endl;
            valcount++;
            D_ASSERT(curnode>=numvars && curnode<(numvars+numvals));
            #ifndef NO_DEBUG
            /*
            bool found=false;
            for(SysInt i=0; i<vars_in_scc.size(); i++)
            {
                if(var_array[vars_in_scc[i]].inDomain(curnode+dom_min-numvars))
                {
                    found=true;
                }
            }*/
            // D_ASSERT(found);  // it is safe to take out this test. But how did we get to this value?
            #endif

            SysInt lowlinkvar=-1;
            #if !UseIncGraph
            for(SysInt i=0; i<(SysInt)vars_in_scc.size(); i++)
            {
                SysInt newnode=vars_in_scc[i];
            #else
            for(SysInt i=0; i<adjlistlength[curnode]; i++)
            {
                SysInt newnode=adjlist[curnode][i];
            #endif
                if(matching[newnode]!=curnode-numvars+dom_min)   // if the value is not in the matching.
                {
                    #if !UseIncGraph
                    if(var_array[newnode].inDomain(curnode+dom_min-numvars))
                    #endif
                    {
                        //newnode=varvalmatching[newnode]-dom_min+numvars;  // Changed here for merge nodes
                        if(!visited.in(newnode))
                        {
                            #if InternalDT
                                GCCPRINT("Adding DT for var " << newnode << " val " << curnode-numvars+dom_min);
                                idt.addwatch(newnode, curnode-numvars+dom_min);
                            #endif
                            visit(newnode, false, upper, lower, matching, usage);
                            if(lowlink[newnode]<lowlink[curnode])
                            {
                                lowlink[curnode]=lowlink[newnode];
                                lowlinkvar=-1;   // Would be placing a watch where there already is one.
                            }
                        }
                        else
                        {
                            // Already visited newnode
                            if(in_tstack.in(newnode) && dfsnum[newnode]<lowlink[curnode])
                            {
                                lowlink[curnode]=dfsnum[newnode];
                                lowlinkvar=newnode;
                            }
                        }
                    }
                }
            }
            (void)lowlinkvar; // to block warning
            if(true //include_sink
                && usage[curnode-numvars]>lower[curnode-numvars])  // adaptation for GCC instead of the following comment.
            //valinlocalmatching.in(curnode-numvars))
            {
                SysInt newnode=numvars+numvals;
                if(!visited.in(newnode))
                {
                    visit(newnode, false, upper, lower, matching, usage);
                    if(lowlink[newnode]<lowlink[curnode])
                    {
                        lowlink[curnode]=lowlink[newnode];
                        lowlinkvar=-1;
                    }
                }
                else
                {
                    // Already visited newnode
                    if(in_tstack.in(newnode) && dfsnum[newnode]<lowlink[curnode])
                    {
                        lowlink[curnode]=dfsnum[newnode];
                        lowlinkvar=-1;
                    }
                }
            }
            // Where did the low link value come from? insert that edge into watches.
            #if InternalDT
                if(lowlinkvar!=-1)
                {
                    GCCPRINT("Adding DT for var " << lowlinkvar << " val " << curnode-numvars+dom_min);
                    idt.addwatch(lowlinkvar, curnode-numvars+dom_min);
                }
            #endif

        }

        //cout << "On way back up, curnode:" << curnode<< ", lowlink:"<<lowlink[curnode]<< ", dfsnum:"<<dfsnum[curnode]<<endl;
        if(lowlink[curnode]==dfsnum[curnode])
        {
            // Did the SCC split?
            // Perhaps we traversed all vars but didn't unroll the recursion right to the top.
            // !toplevel . Or perhaps we didn't traverse all the variables. (or all values.)
            // I think these two cases cover everything.
            if(!toplevel || varcount<(SysInt)vars_in_scc.size() || valcount<(SysInt)vals_in_scc.size())
            {
                scc_split=true;  // The SCC has split and there is some work to do later.
            }

            // Doing something with the components should not be necessary unless the scc has split.
            // The first SCC found is deep in the tree, so the flag will be set to its final value
            // the first time we are here.
            // so it is OK to assume that scc_split has been
            // set correctly before we do the following.
            if(scc_split)
            {
                // For each variable and value, write it to the scc array.
                // If its the last one, flip the bit.

                varinlocalmatching.clear();  // Borrow this datastructure for a minute.

                GCCPRINT("Writing new SCC:");
                bool containsvars=false, containsvals=false;
                for(vector<SysInt>::iterator tstackit=tstack.end()-1;  ; --tstackit)
                {
                    SysInt copynode=(*tstackit);

                    if(copynode!=numvars+numvals) // if it is not t
                    {
                        if(copynode<numvars) containsvars=true;
                        else containsvals=true;

                        SysInt temp=SCCs[sccindex];
                        SysInt tempi=varToSCCIndex[copynode];

                        SCCs[sccindex]=copynode;
                        varToSCCIndex[copynode]=sccindex;

                        SCCs[tempi]=temp;
                        varToSCCIndex[temp]=tempi;
                        sccindex++;

                        if(copynode<numvars)
                        {
                            varinlocalmatching.insert(copynode);
                        }
                    }

                    if(copynode==curnode)
                    {
                        // Beware it might be an SCC containing just one value.
                        // or just t
                        // sccindex is the first index of a new SCC,
                        // so insert the marker at sccindex-1, the end of the
                        // previous SCC.
                        if(containsvars || containsvals)   //containsvars
                        {
                            GCCPRINT("Inserting split point at "<< sccindex-1 << " SCCs:" << SCCs);
                            #if !RemoveAssignedVars
                                // If doing the usual SCC dynamic partitioning.
                                SCCSplit.remove(sccindex-1);
                            #endif
                        }

                        // The one written last was the last one in the SCC.
                        break;
                    }

                    // Should be no split points in the middle of writing an SCC.
                    //D_ASSERT(copynode==curnode || copynode>=numvars || SCCSplit.isMember(sccindex-1));
                }
                // Just print more stuff here.


                // For each value, iterate through the current
                // SCC and remove it from any other variable other
                // than the one in this SCC.
                //cout << "Starting loop"<<endl;
                //if(containsvars) // why is this OK? because of bug above, in case where numnode is a variable.
                {
                    while(true)
                    {
                        SysInt copynode=(*(tstack.end()-1));

                        tstack.pop_back();
                        in_tstack.remove(copynode);

                        if(copynode>=numvars && copynode!=(numvars+numvals))
                        {
                            // It's a value. Iterate through old SCC and remove it from
                            // any variables not in tempset.
                            //cout << "Trashing value "<< copynode+dom_min-numvars << endl;
                            for(SysInt i=0; i<(SysInt)vars_in_scc.size(); i++)
                            {
                                SysInt curvar=vars_in_scc[i];
                                if(!varinlocalmatching.in(curvar))
                                {
                                    // var not in tempset so might have to do some test against matching.
                                    // Why doing this test? something wrong with the assigned variable optimization?
                                    if(matching[curvar]!=copynode+dom_min-numvars)
                                    {
                                        GCCPRINT("Removing var: "<< curvar << " val:" << copynode+dom_min-numvars);
                                        if(var_array[curvar].inDomain(copynode+dom_min-numvars))
                                        {
                                            var_array[curvar].removeFromDomain(copynode+dom_min-numvars);
                                            #if UseIncGraph
                                                adjlist_remove(curvar, copynode-numvars+dom_min);
                                            #endif
                                        }
                                    }
                                }
                            }
                        }

                        if(copynode==curnode)
                        {
                            break;
                        }
                    }
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Propagate to capacity variables.

    inline void prop_capacity()
    {
        if(Strongcards)
        {
            prop_capacity_strong();
        }
        else
        {
            prop_capacity_simple();
        }
    }

    void prop_capacity_linear()
    {
        // We know we have incgraph.
        // use augpath as a temporary place to count assignments for each value.
        augpath.clear();
        augpath.resize(numvals, 0);

        for(SysInt i=0; i<(SysInt)var_array.size(); i++)
        {
            if(var_array[i].isAssigned())
            {
                DomainInt val=var_array[i].getAssignedValue();
                augpath[val-dom_min]++;
            }
        }
        // Set bounds
        for(SysInt i=0; i<(SysInt)val_array.size(); i++)
        {
            SysInt val=val_array[i];
            capacity_array[i].setMin(augpath[val-dom_min]);
            capacity_array[i].setMax(adjlistlength[val-dom_min+numvars]);
        }
    }

    void prop_capacity_simple()
    {
        // basic prop from main vars to cap variables. equiv to occurrence constraints I think.
        // NEEDS TO BE IMPROVED. but it would be quadratic (nd) whatever I do.
        // Nope, when using incgraph only need to count assignments of each variable
        // which is linear in r.
        for(SysInt i=0; i<(SysInt)val_array.size(); i++)
        {
            SysInt val=val_array[i];
            if(lower[val-dom_min] != upper[val-dom_min])
            {
                prop_capacity_simple(val);
            }
        }
    }

    void prop_capacity_simple_scc_incgraph()
    {
        // requires vars_in_scc and vals_in_scc.
        // Buggy! It doesnt work because vars not in vars_in_scc may be
        // assigned to a val in vals_in_scc.
        // Count assigned values using augpath as a temporary.
        augpath.clear();
        augpath.resize(numvals,0);
        for(SysInt i=0; i<(SysInt)vars_in_scc.size(); i++)
        {
            SysInt var=vars_in_scc[i];
            if(var_array[var].isAssigned())
            {
                augpath[var_array[var].getAssignedValue()-dom_min]++;
            }
        }
        // Set bounds
        for(SysInt i=0; i<(SysInt)vals_in_scc.size(); i++)
        {
            SysInt val=vals_in_scc[i];
            SysInt capidx=val_to_cap_index[val-dom_min];
            capacity_array[capidx].setMin(augpath[val-dom_min]);
            capacity_array[capidx].setMax(adjlistlength[val-dom_min+numvars]);
        }
    }

    void prop_capacity_simple(SysInt val)
    {
        SysInt i=val_to_cap_index[val-dom_min];
        D_ASSERT(i!=-1);
        D_ASSERT(lower[val-dom_min]<upper[val-dom_min]);
        // called above or directly from do_gcc_prop_scc when using SCCCARDS
        #if !UseIncGraph
            SysInt mincap=0;
            SysInt maxcap=0;
            for(SysInt j=0; j<numvars; j++)
            {
                if(var_array[j].inDomain(val))
                {
                    maxcap++;
                    if(var_array[j].isAssigned())
                        mincap++;
                }
            }
            capacity_array[i].setMin(mincap);
            capacity_array[i].setMax(maxcap);
        #else
            // This is a little odd because the adjacency list might be out of date
            // because of pruning done earlier on another cap var.
            // (if some vars are shared between primary and capacity vars).
            // So need to check that assigned vars in the adjlist are actually
            // assigned to val.
            // It's odd because we're using a mixture of real var state and
            // the internal data structure.
            if(val>= dom_min && val<=dom_max)
            {
                SysInt mincap=0;
                for(SysInt vari=0; vari<adjlistlength[val-dom_min+numvars]; vari++)
                {
                    SysInt var=adjlist[val-dom_min+numvars][vari];
                    if(var_array[var].isAssigned() && var_array[var].getAssignedValue()==val)
                        mincap++;
                }
                capacity_array[i].setMin(mincap);
                capacity_array[i].setMax(adjlistlength[val-dom_min+numvars]);
            }  // else the cap will already have been set to 0.
        #endif
    }

    void prop_capacity_strong()
    {
        // Lower bounds.
        GCCPRINT("In prop_capacity_strong");

        // Temporary measure.
        vars_in_scc.clear();
        for(SysInt i=0; i<numvars; i++) vars_in_scc.push_back(i);

        vals_in_scc.clear();
        for(SysInt i=dom_min; i<=dom_max; i++) vals_in_scc.push_back(i);

        for(SysInt validx=0; validx<(SysInt)val_array.size(); validx++)
        {
            SysInt value=val_array[validx];

            if(value>=dom_min && value<=dom_max
                && lower[value-dom_min]!=upper[value-dom_min])
            {
                prop_capacity_strong_scc(value);
            }
        }
    }

    void prop_capacity_strong_scc(SysInt value)
    {
        GCCPRINT("In prop_capacity_strong_scc(value)");
        // use the matching -- change it by lowering flow to value.
        GCCPRINT("Calling bfsmatching_card_lowerbound for value "<< value);
        // assumes vars_in_scc and vals_in_scc are already populated.

        SysInt newlb=bfsmatching_card_lowerbound(value, lower[value-dom_min]);
        GCCPRINT("bfsmatching_card_lowerbound Returned " << newlb);
        SysInt validx=val_to_cap_index[value-dom_min];
        if(newlb > capacity_array[validx].getMin())
        {
            GCCPRINT("Improved lower bound "<< newlb);
            capacity_array[validx].setMin(newlb);
            lower[value-dom_min]=newlb;
        }

        GCCPRINT("Calling card_upperbound for value "<< value);
        SysInt newub=card_upperbound(value, upper[value-dom_min]);
        GCCPRINT("card_upperbound Returned " << newub);

        if(newub < capacity_array[validx].getMax())
        {
            GCCPRINT("Improved upper bound "<< newub);
            capacity_array[validx].setMax(newub);
            upper[value-dom_min]=newub;
        }
    }

    // function to re-maximise a matching without using a particular value.
    // Used to find a new lowerbound for the value.
    // Changed copy of bfsmatching_gcc method above.

    // should stop when we reach the existing bound.
    inline SysInt bfsmatching_card_lowerbound(SysInt forbiddenval, SysInt existinglb)
    {
        // lower and upper are indexed by value-dom_min and provide the capacities.
        // usage is the number of times a value is used in the matching.

        if(existinglb==usage[forbiddenval-dom_min])
        {
            //bound already supported
            return existinglb;
        }

        #ifdef CAPBOUNDSCACHE
        if(boundsupported[(forbiddenval-dom_min)*2]==existinglb)
        {
            PROP_INFO_ADDONE(Counter8);
            return existinglb;
        }
        #endif
        PROP_INFO_ADDONE(Counter9);
        // current sccs are contained in vars_in_scc and vals_in_scc
        // back up the matching to restore afterwards.
        matchbac=varvalmatching;
        usagebac=usage;

        // clear out forbiddenval
        // instead of clearing it out, can we do something else?
        /*usage[forbiddenval-dom_min]=0;
        for(SysInt i=0; i<numvars; i++)
        {
            if(varvalmatching[i]==forbiddenval)
            {
                varvalmatching[i]=dom_min-1;
                newlb++;
            }
        }*/
        SysInt newlb=usage[forbiddenval-dom_min];  // new lower bound. When this passes existinglb, we can stop.

        /*for(SysInt startvarscc=0; startvarscc<vars_in_scc.size(); startvarscc++)
        {
            SysInt startvar=vars_in_scc[startvarscc];
            if(varvalmatching[startvar]==forbiddenval)
            {
                varvalmatching[startvar]=dom_min-1;
                usage[forbiddenval-dom_min]--;
            }
        }*/

        // Flip the graph around, so it's like the alldiff case now.
        // follow an edge in the matching from a value to a variable,
        // follow edges not in the matching from variables to values.

        // IMPROVE HERE: This implementation is time optimal O(r^2 d)
        // but it can duplicate work: if it looks for a path for x1, and
        // doesn't find one, then looks for a path for x2, it can revisit
        // nodes that were seen in the search for x1.

        // The comment above could perhaps be true for DFS where once a node is
        // expanded the subtree under it is explored for aug paths.

        #if UseIncGraph
        for(SysInt startvari=0; startvari<adjlistlength[forbiddenval-dom_min+numvars] && newlb>existinglb; startvari++)
        {
            SysInt startvar=adjlist[forbiddenval-dom_min+numvars][startvari];
        #else
        for(SysInt startvarscc=0; startvarscc<(SysInt)vars_in_scc.size() && newlb>existinglb; startvarscc++)
        {
            SysInt startvar=vars_in_scc[startvarscc];
        #endif
            if(varvalmatching[startvar]==forbiddenval)
            {
                varvalmatching[startvar]=dom_min-1;
                usage[forbiddenval-dom_min]--;
                GCCPRINT("Searching for augmenting path for var: " << startvar);
                fifo.clear();  // this should be constant time but probably is not.
                fifo.push_back(startvar);
                visited.clear();
                visited.insert(startvar);
                bool finished=false;
                while(!fifo.empty() && !finished)
                {
                    // pop a vertex and expand it.
                    SysInt curnode=fifo.front();
                    fifo.pop_front();
                    GCCPRINT("Popped vertex " << (curnode<numvars? "(var)":"(val)") << (curnode<numvars? curnode : curnode+dom_min-numvars ));
                    if(curnode<numvars)
                    { // it's a variable
                        // follow all edges other than the matching edge.
                        #if !UseIncGraph
                        for(DomainInt valtoqueue=var_array[curnode].getMin(); valtoqueue<=var_array[curnode].getMax(); valtoqueue++)
                        {
                        #else
                        for(SysInt valtoqueuei=0; valtoqueuei<adjlistlength[curnode]; valtoqueuei++)
                        {
                            SysInt valtoqueue=adjlist[curnode][valtoqueuei];
                        #endif
                            // For each value, check if it terminates an odd alternating path
                            // and also queue it if it is suitable.
                            SysInt validx=valtoqueue-dom_min+numvars;
                            if(valtoqueue!=varvalmatching[curnode]
                                && valtoqueue!=forbiddenval  // added for this method.
                            #if !UseIncGraph
                                && var_array[curnode].inDomain(valtoqueue)
                            #endif
                                && !visited.in(validx) )
                            {
                                //D_ASSERT(find(vals_in_scc.begin(), vals_in_scc.end(), valtoqueue)!=vals_in_scc.end()); // the value is in the scc.
                                // Does this terminate an augmenting path?
                                if(usage[valtoqueue-dom_min]<upper[valtoqueue-dom_min])
                                {
                                    // valtoqueue terminates an alternating path.
                                    // Unwind and apply the path here
                                    prev[validx]=curnode;
                                    apply_augmenting_path_reverse(validx, startvar);
                                    finished=true;
                                    newlb--;  // update bound counter
                                    break;  // get out of for loop
                                }
                                else
                                {
                                    // queue valtoqueue
                                    visited.insert(validx);
                                    prev[validx]=curnode;
                                    fifo.push_back(validx);
                                }
                            }
                        }  // end for.
                    }
                    else
                    { // popped a value from the stack.
                        D_ASSERT(curnode>=numvars && curnode < numvars+numvals);
                        SysInt stackval=curnode+dom_min-numvars;
                        #if UseIncGraph
                        for(SysInt vartoqueuei=0; vartoqueuei<adjlistlength[curnode]; vartoqueuei++)
                        {
                            SysInt vartoqueue=adjlist[curnode][vartoqueuei];
                        #else
                        for(SysInt vartoqueuescc=0; vartoqueuescc<(SysInt)vars_in_scc.size(); vartoqueuescc++)
                        {
                            SysInt vartoqueue=vars_in_scc[vartoqueuescc];
                        #endif
                            // For each variable which is matched to stackval, queue it.
                            if(!visited.in(vartoqueue)
                                && varvalmatching[vartoqueue]==stackval)
                            {
                                D_ASSERT(var_array[vartoqueue].inDomain(stackval));
                                // there is an edge from stackval to vartoqueue.
                                // queue vartoqueue
                                visited.insert(vartoqueue);
                                prev[vartoqueue]=curnode;
                                fifo.push_back(vartoqueue);
                            }
                        }  // end for.
                    }  // end value
                }  // end while
            }
        }

        GCCPRINT("maximum matching:" << varvalmatching);

        if(newlb==existinglb)
        {
            GCCPRINT("Stopped because new lower bound would be less than or equal the existing lower bound.");
        }

        #ifdef CAPBOUNDSCACHE
        boundsupported[(forbiddenval-dom_min)*2]=usage[forbiddenval-dom_min];
        DynamicTrigger* dt=dynamic_trigger_start();
        dt+=(numvars*numvals);  // skip over the first block of triggers
        dt+=val_to_cap_index[forbiddenval-dom_min]*(val_array.size()+numvars)*2;  // move to the area for the value.
        //dt+=(val_array.size()+numvars);  // move to upper bound area
        // now put down the triggers for varvalmatching and usage
        for(SysInt i=0; i<numvars; i++)
        {
            D_ASSERT((dt+i)->trigger_info() == (forbiddenval-dom_min)*2);
            var_array[i].addDynamicTrigger(dt+i, DomainRemoval, varvalmatching[i]);
        }
        for(SysInt i=0; i<val_array.size(); i++)
        {
            D_ASSERT((dt+numvars+i)->trigger_info() == (forbiddenval-dom_min)*2);

            if(val_array[i]>= dom_min && val_array[i]<= dom_max)
            {
                capacity_array[i].addDynamicTrigger(dt+numvars+i, DomainRemoval, usage[val_array[i]-dom_min]);
            }
            else
            {
                capacity_array[i].addDynamicTrigger(dt+numvars+i, DomainRemoval, 0);
            }
        }
        #endif

        varvalmatching=matchbac;
        usage=usagebac;

        return newlb;
    }

    // By changing the flow in card_upperbound and not restoring it,
    // might compromize the the worst-case analysis of the lowerbound thing...
    // it requires the same flow so that it only looks for a mazimum of r paths.

    // Actually might be better to not restore the matching here, because it calls
    // lowerbound then upperbound for value a, then moves onto next value b.
    // This means occurrences of a are maximized before looking at b.
    // therefore occurrences of b might be reduced.
    // overall though, not sure if worst-case analysis is not compromised.

    inline SysInt card_upperbound(SysInt value, SysInt existingub)
    {
        // lower and upper are indexed by value-dom_min and provide the capacities.
        // usage is the number of times a value is used in the matching.

        if(existingub==usage[value-dom_min])
        {
            // bound is already supported
            return existingub;
        }

        #ifdef CAPBOUNDSCACHE
        if(boundsupported[(value-dom_min)*2+1]==existingub)
        {
            PROP_INFO_ADDONE(Counter8);
            return existingub;
        }
        #endif
        PROP_INFO_ADDONE(Counter9);
        // current sccs are contained in vars_in_scc and vals_in_scc

        SysInt startvalindex=value-dom_min;
        while(usage[startvalindex]<existingub)
        {
            // usage of value needs to increase. Construct an augmenting path starting at value.
            GCCPRINT("Searching for augmenting path for val: " << value);
            // Matching edge lost; BFS search for augmenting path to fix it.
            fifo.clear();  // this should be constant time but probably is not.
            fifo.push_back(startvalindex+numvars);
            visited.clear();
            visited.insert(startvalindex+numvars);
            bool finished=false;
            while(!fifo.empty() && !finished)
            {
                // pop a vertex and expand it.
                SysInt curnode=fifo.front();
                fifo.pop_front();
                GCCPRINT("Popped vertex " << (curnode<numvars? "(var)":"(val)") << (curnode<numvars? curnode : curnode+dom_min-numvars ));
                if(curnode<numvars)
                { // it's a variable
                    // follow the matching edge, if there is one.
                    SysInt valtoqueue=varvalmatching[curnode];
                    if(valtoqueue!=dom_min-1
                        && !visited.in(valtoqueue-dom_min+numvars))
                    {
                        D_ASSERT(var_array[curnode].inDomain(valtoqueue));
                        SysInt validx=valtoqueue-dom_min+numvars;
                        if(usage[valtoqueue-dom_min]>lower[valtoqueue-dom_min])
                        {
                            // can reduce the flow of valtoqueue to increase startval.
                            prev[validx]=curnode;
                            apply_augmenting_path(validx, startvalindex+numvars);
                            finished=true;
                        }
                        else
                        {
                            visited.insert(validx);
                            prev[validx]=curnode;
                            fifo.push_back(validx);
                        }
                    }
                }
                else
                { // popped a value from the stack.
                    D_ASSERT(curnode>=numvars && curnode < numvars+numvals);
                    SysInt stackval=curnode+dom_min-numvars;
                    #if !UseIncGraph
                    for(SysInt vartoqueuescc=0; vartoqueuescc<vars_in_scc.size(); vartoqueuescc++)
                    {
                        SysInt vartoqueue=vars_in_scc[vartoqueuescc];
                    #else
                    for(SysInt vartoqueuei=0; vartoqueuei<adjlistlength[curnode]; vartoqueuei++)
                    {
                        SysInt vartoqueue=adjlist[curnode][vartoqueuei];
                    #endif
                        // For each variable, check if it terminates an odd alternating path
                        // and also queue it if it is suitable.
                        if(!visited.in(vartoqueue)
                            #if !UseIncGraph
                            && var_array[vartoqueue].inDomain(stackval)
                            #endif
                            && varvalmatching[vartoqueue]!=stackval)   // Need to exclude the matching edges????
                        {
                            // there is an edge from stackval to vartoqueue.
                            if(varvalmatching[vartoqueue]==dom_min-1)   // This should never be true...
                            {
                                // vartoqueue terminates an odd alternating path.
                                // Unwind and apply the path here
                                prev[vartoqueue]=curnode;
                                apply_augmenting_path(vartoqueue, startvalindex+numvars);
                                finished=true;
                                break;  // get out of for loop
                            }
                            else
                            {
                                // queue vartoqueue
                                visited.insert(vartoqueue);
                                prev[vartoqueue]=curnode;
                                fifo.push_back(vartoqueue);
                            }
                        }
                    }  // end for.
                }  // end value
            }  // end while
            if(!finished)
            {   // no augmenting path found
                GCCPRINT("No augmenting path found.");
                // restore the matching to its state before the algo was called.
                break;
            }

        }  // end while

        //varvalmatching=matchbac;
        //usage=usagebac;
        #ifdef CAPBOUNDSCACHE
        boundsupported[(value-dom_min)*2+1]=usage[startvalindex];
        DynamicTrigger* dt=dynamic_trigger_start();
        dt+=(numvars*numvals);  // skip over the first block of triggers
        dt+=val_to_cap_index[value-dom_min]*(val_array.size()+numvars)*2;  // move to the area for the value.
        dt+=(val_array.size()+numvars);  // move to upper bound area
        // now put down the triggers for varvalmatching and usage
        for(SysInt i=0; i<numvars; i++)
        {
            D_ASSERT((dt+i)->trigger_info() == (value-dom_min)*2+1);
            var_array[i].addDynamicTrigger(dt+i, DomainRemoval, varvalmatching[i]);
        }
        for(SysInt i=0; i<val_array.size(); i++)
        {
            D_ASSERT((dt+numvars+i)->trigger_info() == (value-dom_min)*2+1);
            if(val_array[i]>= dom_min && val_array[i]<= dom_max)
            {
                capacity_array[i].addDynamicTrigger(dt+numvars+i, DomainRemoval, usage[val_array[i]-dom_min]);
            }
            else
            {
                capacity_array[i].addDynamicTrigger(dt+numvars+i, DomainRemoval, 0);
            }
        }
        #endif

        return usage[startvalindex];
    }

    typedef typename CapArray::value_type CapVarRef;
    virtual AbstractConstraint* reverse_constraint()
    {
        // use a watched-or of NotOccurrenceEqualConstraint, i.e. the negation of occurrence
        vector<AbstractConstraint*> con;
        for(SysInt i=0; i<(SysInt)capacity_array.size(); i++)
        {
            NotOccurrenceEqualConstraint<VarArray, DomainInt, CapVarRef>*
                t=new NotOccurrenceEqualConstraint<VarArray, DomainInt, CapVarRef>(
                    stateObj, var_array, val_array[i], capacity_array[i]);
            con.push_back((AbstractConstraint*) t);
        }
        return new Dynamic_OR(stateObj, con);
    }
};

#endif
