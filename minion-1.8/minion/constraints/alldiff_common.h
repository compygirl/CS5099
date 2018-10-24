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
#include <stdlib.h>
#include <iostream>
#include <vector>
#include <deque>
#include <algorithm>
#include <utility>

using namespace std;

#include "alldiff_gcc_shared.h"

// includes for reverse constraint.
#include "constraint_equal.h"
#include "../dynamic_constraints/dynamic_new_or.h"
#include "../constraints/constraint_checkassign.h"

// The implementation of the alldiff GAC algorithm,
// shared between gacalldiff and the (now defunct) dynamicalldiff.


// whether to backtrack the matching.
// This one is a slowdown.
// #define BTMATCHING

// This one seems to break even.
// Whether to use a TMS to track whether spare values are present in the scc
// #define SPAREVALUESOPT

// Reverse the var list. Leave this switched on.
#define REVERSELIST

// Check domain size -- if it is greater than numvars, then no need to wake the constraint.
//#define CHECKDOMSIZE

// Process SCCs independently
#define SCC

// Warning: if this is not defined true, then watchedalldiff probably won't do anything.
#define UseWatches 0

// Optimize the case where a value was assigned. Only works in the presence of SCC
#define ASSIGNOPT

// Use the special queue
#define SPECIALQUEUE

// store matching from one run to the next.
#define INCREMENTALMATCH

// Use BFS instead of HK
#define BFSMATCHING

// Use the new hopcroft-karp implementation.
//#define NEWHK

// Incremental graph stored in adjlist
#define UseIncGraph 0

// Use staging a la Schulte and Stuckey
#define STAGED

#ifdef P
#undef P
#endif

#ifdef PLONG
#undef PLONG
#endif

#define P(x)
//#define P(x) cout << x << endl
//#define PLONG

#ifdef PHALLSETSIZE
#undef PHALLSETSIZE
#endif

//#define PHALLSETSIZE(x) cout << x << endl
#define PHALLSETSIZE(x)

template<typename VarArray>
struct GacAlldiffConstraint : public FlowConstraint<VarArray, UseIncGraph>
{
    using FlowConstraint<VarArray, UseIncGraph>::stateObj;
    using FlowConstraint<VarArray, UseIncGraph>::constraint_locked;
    using FlowConstraint<VarArray, UseIncGraph>::adjlist;
    using FlowConstraint<VarArray, UseIncGraph>::adjlistlength;
    using FlowConstraint<VarArray, UseIncGraph>::adjlistpos;
    using FlowConstraint<VarArray, UseIncGraph>::dynamic_trigger_start;
    using FlowConstraint<VarArray, UseIncGraph>::adjlist_remove;
    using FlowConstraint<VarArray, UseIncGraph>::var_array;
    using FlowConstraint<VarArray, UseIncGraph>::dom_min;
    using FlowConstraint<VarArray, UseIncGraph>::dom_max;
    using FlowConstraint<VarArray, UseIncGraph>::numvars;
    using FlowConstraint<VarArray, UseIncGraph>::numvals;
    using FlowConstraint<VarArray, UseIncGraph>::varvalmatching;
    using FlowConstraint<VarArray, UseIncGraph>::valvarmatching;


    using FlowConstraint<VarArray, UseIncGraph>::varinlocalmatching;
    using FlowConstraint<VarArray, UseIncGraph>::valinlocalmatching;
    using FlowConstraint<VarArray, UseIncGraph>::invprevious;

    using FlowConstraint<VarArray, UseIncGraph>::initialize_hopcroft;
    using FlowConstraint<VarArray, UseIncGraph>::hopcroft_wrapper;
    using FlowConstraint<VarArray, UseIncGraph>::hopcroft2_setup;

    virtual string constraint_name()
    {
        return "gacalldiff";
    }

    CONSTRAINT_ARG_LIST1(var_array);

    vector<SysInt> SCCs;    // Variable numbers
    ReversibleMonotonicSet SCCSplit;
    // If !SCCSplit.isMember(anIndex) then anIndex is the last index in an SCC.

    //ReversibleMonotonicSet sparevaluespresent;

    D_DATA(void* SCCSplit2);

    vector<SysInt> varToSCCIndex;  // Mirror of the SCCs array.

    GacAlldiffConstraint(StateObj* _stateObj, const VarArray& _var_array) : FlowConstraint<VarArray, UseIncGraph>(_stateObj, _var_array),
        SCCSplit(_stateObj, _var_array.size())
    //sparevaluespresent(_stateObj, _var_array.size())
    {
      CheckNotBound(var_array, "gacalldiff", "alldiff");
      SCCs.resize(var_array.size());
      varToSCCIndex.resize(var_array.size());
      for(SysInt i=0; i<(SysInt)var_array.size(); ++i)
      {
          SCCs[i]=i;
          varToSCCIndex[i]=i;
      }

      to_process.reserve(var_array.size());

      // Set up data structures
      prev.resize(numvars+numvals, -1);
      initialize_hopcroft();
      initialize_tarjan();

      #ifdef NEWHK
      hopcroft2_setup();
      #endif

      sccs_to_process.reserve(numvars);

      // Initialize matching to satisfy the invariant
      // that the values are all different in varvalmatching.
      // watches DS is used in alldiffgacslow and in debugging.
      #if !defined(DYNAMICALLDIFF) || !defined(NO_DEBUG)
      if(UseWatches) watches.resize(numvars);
      #endif

      for(SysInt i=0; i<numvars ; i++) //&& i<numvals
      {
          varvalmatching[i]=i+dom_min;
          if(i<numvals) valvarmatching[i]=i;

          #if !defined(DYNAMICALLDIFF) || !defined(NO_DEBUG)
          if(UseWatches) watches[i].reserve(numvals, stateObj);
          #endif
      }

      D_DATA(SCCSplit2=getMemory(stateObj).backTrack().request_bytes((sizeof(char) * numvars)));
      D_DATA(for(SysInt i=0; i<numvars; i++) ((char *)SCCSplit2)[i]=1);

  }

  // only used in dynamic version.
  SysInt dynamic_trigger_count()
  {
    // First an array of watches for the matching, then a 2d array of mixed triggers
    // indexed by [var][count] where count is increased from 0 as the triggers are used.
    SysInt numtrigs=0;
    if(UseIncGraph)
    {
        numtrigs+=numvars*numvals; // one for each var-val pair so we know when it is removed.
    }

    #ifdef DYNAMICALLDIFF
    numtrigs+= numvars+numvars*numvals;
    #endif

    // Dynamic alldiff triggers go first, incgraph triggers are after that
    // so places which access incgraph triggers must check if DYNAMICALLDIFF is defined.
    return numtrigs;
  }

  #ifdef DYNAMICALLDIFF
  inline DynamicTrigger * get_dt(SysInt var, SysInt counter)
  {
      // index the square array of dynamic triggers from dt
      D_ASSERT(bt_triggers_start == dynamic_trigger_start()+numvars);
      D_ASSERT(counter<numvals);
      D_ASSERT(var<numvars);
      D_ASSERT((bt_triggers_start+(var*numvals)+counter) >= dynamic_trigger_start());
      D_ASSERT((bt_triggers_start+(var*numvals)+counter) < dynamic_trigger_start() + dynamic_trigger_count());

      return bt_triggers_start+(var*numvals)+counter;
  }
  #endif

  // Should only be used in non-dynamic version.
  #ifndef DYNAMICALLDIFF
  virtual triggerCollection setup_internal()
  {
    triggerCollection t;
    SysInt array_size = var_array.size();
    for(SysInt i = 0; i < array_size; ++i)
      t.push_back(make_trigger(var_array[i], Trigger(this, i), DomainChanged));
    return t;
  }
  #endif

  typedef typename VarArray::value_type VarRef;
  virtual AbstractConstraint* reverse_constraint()
  { // w-or of pairwise equality.

      /// solely for reify exps
      return forward_check_negation(stateObj, this);

      vector<AbstractConstraint*> con;
      for(SysInt i=0; i<(SysInt)var_array.size(); i++)
      {
          for(SysInt j=i+1; j<(SysInt)var_array.size(); j++)
          {
              EqualConstraint<VarRef, VarRef>* t=new EqualConstraint<VarRef, VarRef>(stateObj, var_array[i], var_array[j]);
              con.push_back((AbstractConstraint*) t);
          }
      }
      return new Dynamic_OR(stateObj, con);
  }

  smallset to_process;  // set of vars to process.

  #if !defined(DYNAMICALLDIFF) || !defined(NO_DEBUG)
  vector<smallset_list_bt> watches;
  #endif

  virtual void propagate(DomainInt prop_var_in, DomainDelta)
  {
    const SysInt prop_var = checked_cast<SysInt>(prop_var_in);
    D_ASSERT(prop_var>=0 && prop_var<(SysInt)var_array.size());

    // return if all the watches are still in place.
    #ifndef BTMATCHING
    if(UseWatches && !to_process.in(prop_var)
        && var_array[prop_var].inDomain(varvalmatching[prop_var]))     // This still has to be here, because we still wake up if the matchingis broken.
    #else
    if(UseWatches && !to_process.in(prop_var))
    #endif
    {
        smallset_list_bt& watch = watches[prop_var];
        short * list = ((short *) watch.list);
        SysInt count=list[watch.maxsize];
        bool valout=false;

        for(SysInt i=0; i<count; i++)
        {
            P("Checking var "<< prop_var << " val " << list[i]+dom_min);
            if(!var_array[prop_var].inDomain(list[i]+dom_min))
            {
                valout=true;
                break;
            }
        }
        if(!valout)
        {
            P("None of the watches were disturbed. Saved a call with watches.");
            return;
        }
    }
    #ifdef CHECKDOMSIZE
    // If the domain size is >= numvars, then return.
    //if(!constraint_locked)
    {
        SysInt count=0;
        for(DomainInt i=var_array[prop_var].getMin(); i<=var_array[prop_var].getMax(); i++)
        {
            if(var_array[prop_var].inDomain(i))
            {
                count++;
            }
        }
        if(count>=numvars)
            return;
    }
    // could improve domain counting by also applying it when
    // the constraint is already queued, DONE since it might avoid calls to Tarjan's.
    // i.e. some vars may not need to be queued in to_process.
    #endif

    #ifdef STAGED
    if(var_array[prop_var].isAssigned())
    {
        DomainInt assignedval=var_array[prop_var].getAssignedValue();
        for(SysInt i=0; i<numvars; i++)
        {
            if(i!=prop_var && var_array[i].inDomain(assignedval))
            {
                var_array[i].removeFromDomain(assignedval);
                PHALLSETSIZE(1);
                #if UseIncGraph
                    adjlist_remove(i, assignedval);
                #endif
            }
        }
    }
    #endif

    if(!to_process.in(prop_var))
    {
        to_process.insert(prop_var);
    }

    if(!constraint_locked)
    {
        #ifdef SPECIALQUEUE
        constraint_locked = true;
        getQueue(stateObj).pushSpecialTrigger(this);
        #else
        #ifndef SCC
        do_prop_noscc();
        #else
        do_prop();
        #endif
        #endif
    }
  }

  virtual void propagate(DynamicTrigger* trig)
  {
      #if UseIncGraph
      DynamicTrigger* dtstart=dynamic_trigger_start();
      #ifdef DYNAMICALLDIFF
      dtstart+=numvars+numvars*numvals;
      #endif
      if(trig>=dtstart && trig< dtstart+numvars*numvals)  // does this trigger belong to incgraph?
      {
          SysInt diff=trig-dtstart;
            SysInt var=diff/numvals;
            SysInt validx=diff%numvals;
            if(adjlistpos[validx+numvars][var]<adjlistlength[validx+numvars])
            {
                P("Removing var, val " << var << ","<< (validx+dom_min) << " from adjacency list.");
                adjlist_remove(var, validx+dom_min); //validx, adjlistpos[validx][var]);
            }
            return;
      }
      #endif

      // get variable number from the trigger
    SysInt prop_var = trig->trigger_info();
    #ifdef PLONG
    // check that some value has been disturbed; otherwise the watches are malfunctioning.
    if(var_array[prop_var].inDomain(varvalmatching[prop_var]))
    {
        smallset_list_bt& watch = watches[prop_var];
        short * list = ((short *) watch.list);
        SysInt count=list[watch.maxsize];
        bool valout=false;

        for(SysInt i=0; i<count; i++)
        {
            P("Checking var "<< prop_var << " val " << list[i]+dom_min);
            if(!var_array[prop_var].inDomain(list[i]+dom_min))
            {
                valout=true;
                break;
            }
        }
        if(!valout)
        {
            // none of the watches were disturbed.
            cout << "None of the watches in the DS were disturbed. BT triggers must not match with watches DS." <<endl;
            cout << "Variable " << prop_var <<", val in matching: " << varvalmatching[prop_var] << endl;
            D_ASSERT(false);
        }
    }
    #endif

    #ifdef CHECKDOMSIZE
    // If the domain size is >= numvars, then return.
    // WHY IS THIS HERE WHEN checkdomsize and dynamic triggers don't work together??
    SysInt count=0;
    for(DomainInt i=var_array[prop_var].getMin(); i<=var_array[prop_var].getMax(); i++)
    {
        if(var_array[prop_var].inDomain(i))
        {
            count++;
        }
    }
    if(count>=numvars)
        return;
    #endif

    #ifdef STAGED
    if(var_array[prop_var].isAssigned())
    {
        DomainInt assignedval=var_array[prop_var].getAssignedValue();
        for(SysInt i=0; i<numvars; i++)
        {
            if(i!=prop_var && var_array[i].inDomain(assignedval))
            {
                var_array[i].removeFromDomain(assignedval);
                PHALLSETSIZE(1);
                #if UseIncGraph
                    adjlist_remove(i, assignedval);
                #endif
            }
        }
    }
    #endif

    if(!to_process.in(prop_var))
    {
        to_process.insert(prop_var);
    }

    if(!constraint_locked)
    {
        #ifdef SPECIALQUEUE
        constraint_locked = true;
        getQueue(stateObj).pushSpecialTrigger(this);
        #else
        #ifndef SCC
        do_prop_noscc();
        #else
        do_prop();
        #endif
        #endif
    }
  }


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
    do_prop();
    #else
    do_prop_noscc();
    #endif
    to_process.clear();
  }


  void do_prop()
  {
    PROP_INFO_ADDONE(AlldiffGacSlow);

    #ifdef DYNAMICALLDIFF
    bt_triggers_start=dynamic_trigger_start()+numvars;
    #endif

    #ifdef PLONG
    cout << "Entering do_prop."<<endl;
    cout << "Varvalmatching:" <<varvalmatching<<endl;
    cout << "SCCs:" << SCCs <<endl;
    cout << "SCCSplit: ";
    for(SysInt i=0; i<numvars; i++)
    {
        cout << (SCCSplit.isMember(i)?"1, ":"0, ");
    }
    cout <<endl;
    cout << "varToSCCIndex: "<< varToSCCIndex<<endl;
    cout << "Domains (remember that var_array is reversed):" <<endl;
    for(SysInt i=0; i<numvars; i++)
    {
        cout << "var:" << i << " vals:" ;
        for(SysInt j=dom_min; j<=dom_max; j++)
        {
            if(var_array[i].inDomain(j))
            {
                cout << j <<", ";
            }
        }
        cout << endl;
    }
    // Check the matching is valid.
    for(SysInt i=0; i<numvars; i++)
    {
        if(!var_array[i].inDomain(varvalmatching[i]))
        {
            cout << "val in matching removed: " << i << ", " << varvalmatching[i] <<endl;
        }
        for(SysInt j=i+1; j<numvars; j++)
        {
            D_ASSERT(varvalmatching[i]!=varvalmatching[j]);
            D_ASSERT(SCCs[i]!=SCCs[j]);
        }
        D_ASSERT(SCCSplit.isMember(i) == (((char*)SCCSplit2)[i]==1));
        cout << (SysInt)SCCSplit.isMember(i) << ", " << (SysInt) (((char*)SCCSplit2)[i]==1) << endl;
        // The matches correspond.
        #ifndef BFSMATCHING
        D_ASSERT(valvarmatching[varvalmatching[i]-dom_min]==i);
        #endif
    }

    #ifdef DYNAMICALLDIFF
    // Can't yet check all the watches are in the right place, but can check
    // there are the right number of them on each var.
    for(SysInt i=0; i<(SysInt)var_array.size(); i++)
    {
        SysInt count=0;
        DynamicTrigger* trig=get_dt(i, 0);
        while(trig->queue!=NULL && count<numvals)
        {
            count++;
            if(count<numvals) trig=get_dt(i,count);
        }
        D_ASSERT(count==(SysInt)watches[i].size());
    }
    #endif

    // Check that if an element of the matching is removed, then the var is
    // in to_process.
    for(SysInt i=0; i<(SysInt)var_array.size(); i++)
    {
        if(!var_array[i].inDomain(varvalmatching[i]))
        {
            D_ASSERT(to_process.in(i));
        }
    }
    #endif
    // end of debug.

    #ifndef INCREMENTALMATCH
    // clear the matching.
    for(SysInt i=0; i<numvars && i<numvals; i++)
    {
      varvalmatching[i]=i+dom_min;
      valvarmatching[i]=i;
    }
    #endif

    sccs_to_process.clear();
    {
    vector<SysInt>& toiterate = to_process.getlist();
    P("About to loop for to_process variables.");

    for(SysInt i=0; i<(SysInt)toiterate.size(); ++i)
    {
        SysInt tempvar=toiterate[i];


        SysInt sccindex_start=varToSCCIndex[tempvar];
        SysInt sccindex_end=varToSCCIndex[tempvar];

        while(sccindex_start>0 && SCCSplit.isMember(sccindex_start-1))
        {
            sccindex_start--;   // seek the first item in the SCC.
        }

        while(SCCSplit.isMember(sccindex_end) && sccindex_end<(numvars-1))
        {
            sccindex_end++;   // seek the last item in the SCC
        }

        if(!var_array[tempvar].inDomain(varvalmatching[tempvar]))
        {
            // Find the start of the SCC in SCCs
            var_indices.clear();

            // Add a greedy repair here.

            // Actually should queue the SCCs which need to be hopcrofted,
            // and make greedy repairs to var matchings as we process them here.

            P("Varvalmatching:" <<varvalmatching);

            P("start:" << sccindex_start << " end:"<< sccindex_end);

            if(!matching_wrapper(sccindex_start, sccindex_end))
                return;

            #ifdef DYNAMICALLDIFF
            // sync the watches to the matching,
            // since the matching has just changed.
            #ifndef BTMATCHING
            DynamicTrigger * trig = dynamic_trigger_start();
            for(SysInt j=0; j<numvars; j++)
            {
                var_array[j].addWatchTrigger(trig + j, DomainRemoval, varvalmatching[j]);
                P("Adding watch for var " << j << " val " << varvalmatching[j]);
            }
            #endif
            #endif

            P("Fixed varvalmatching:" << varvalmatching);

            // now both varvalmatching and valvarmatching contain
            // a complete matching for the SCC.
            // Also, valinlocalmatching contains the domain values
            // used in this SCC.
        }

        #ifdef ASSIGNOPT
        if(var_array[tempvar].isAssigned())  // Optimize the case where it is assigned.
        #else
        if(false)
        #endif
        {
            // Split tempvar off from the rest of the SCC
            if(SCCSplit.isMember(sccindex_start))
            {
                // Swap it to the front.
                //cout <<"Before swap:" <<SCCs<<endl;
                sccs_to_process.remove(sccindex_start);

                SysInt swapvar=SCCs[sccindex_start];
                SCCs[sccindex_start]=SCCs[varToSCCIndex[tempvar]];
                SCCs[varToSCCIndex[tempvar]]=swapvar;

                varToSCCIndex[swapvar]=varToSCCIndex[tempvar];
                varToSCCIndex[tempvar]=sccindex_start;

                //cout <<"After swap:" <<SCCs <<endl;
                //cout <<"varToSCCIndex:" << varToSCCIndex <<endl;

                // Split the SCCs
                SCCSplit.remove(sccindex_start);
                D_DATA(((char*)SCCSplit2)[sccindex_start]=0);

                sccindex_start++;
                DomainInt tempval=var_array[tempvar].getAssignedValue();

                // Now remove the value from the reduced SCC
                for(SysInt i=sccindex_start; i<=sccindex_end; i++)
                {
                    if(var_array[SCCs[i]].inDomain(tempval))
                    {
                        P("Removing var: "<< SCCs[i] << " val:" << tempval);
                        var_array[SCCs[i]].removeFromDomain(tempval);
                        PHALLSETSIZE(1);
                        #if UseIncGraph
                            adjlist_remove(SCCs[i], tempval);
                        #endif
                        if(getState(stateObj).isFailed()) return;
                    }
                }

                if(sccindex_start<sccindex_end)
                {
                    D_ASSERT(!sccs_to_process.in(sccindex_start));
                    sccs_to_process.insert(sccindex_start);
                }
            }
            // Else: The SCC is unit anyway. Should be no work to do, and do not insert into sccs_to_process.

        }
        else
        {
            // Not assigned, just some vals removed, so
            if(!sccs_to_process.in(sccindex_start) && sccindex_start<sccindex_end)
            {
                sccs_to_process.insert(sccindex_start);
            }
        }

    } // end of loop.
    }


    #ifndef NO_DEBUG
    // Check the matching is valid.
    for(SysInt i=0; i<numvars; i++)
    {
        D_ASSERT(var_array[i].inDomain(varvalmatching[i]));
        for(SysInt j=i+1; j<numvars; j++)
        {
            D_ASSERT(varvalmatching[i]!=varvalmatching[j]);
            D_ASSERT(SCCs[i]!=SCCs[j]);
        }
        D_ASSERT(SCCSplit.isMember(i) == (((char*)SCCSplit2)[i]==1));
        D_ASSERT(SCCs[varToSCCIndex[i]]==i);
    }
    #endif

    // Call Tarjan's for each disturbed SCC.
    {
    vector<SysInt> & toiterate=sccs_to_process.getlist();
    for(SysInt i=0; i<(SysInt)toiterate.size(); i++)
    {
        SysInt j=toiterate[i];

        // remake var_indices for this SCC.
        var_indices.clear();
        for(SysInt k=j; k<numvars; k++)
        {
            #ifdef CHECKDOMSIZE
            if(!var_array[SCCs[k]].inDomain(varvalmatching[SCCs[k]]))
            {
                SysInt l=j; while(SCCSplit.isMember(l) && l<(numvars-1)) l++;
                if(!matching_wrapper(j, l))
                    return;
            }
            #endif

            var_indices.push_back(SCCs[k]);
            if(!SCCSplit.isMember(k)) break;
        }

        //cout << "Running tarjan's on component "<< var_indices <<endl;
        tarjan_recursive(j);
    }
    }


    //  print components out

    /*
    cout<<"SCCs:"<<endl;
    for(SysInt i=0; i<(SysInt)SCCs.size(); i++)
    {
        cout<< SCCs[i] <<endl;
    }
    cout<<"SCCSplit:"<<endl;
    for(SysInt i=0; i<(SysInt)SCCs.size(); i++)
    {
        cout << ((SCCSplit.isMember(i))?"Nosplit":"Split") <<endl;
    }*/

    return;
  }

  // Simpler version which does not maintain SCCs and simply calls hopcroft and
  // visit for the whole set of variables.
  void do_prop_noscc()
  {
    PROP_INFO_ADDONE(AlldiffGacSlow);

    #ifdef DYNAMICALLDIFF
    bt_triggers_start=dynamic_trigger_start()+numvars;
    #endif

    #ifndef INCREMENTALMATCH
    // clear the matching.
    for(SysInt i=0; i<numvars && i<numvals; i++)
    {
      varvalmatching[i]=i+dom_min;
      valvarmatching[i]=i;
    }
    #endif

    #ifdef PLONG
    cout << "Entering do_prop."<<endl;
    cout << "Varvalmatching:" <<varvalmatching<<endl;
    cout << "SCCs:" << SCCs <<endl;
    cout << "SCCSplit: ";
    for(SysInt i=0; i<numvars; i++)
    {
        cout << (SCCSplit.isMember(i)?"1, ":"0, ");
    }
    cout <<endl;
    cout << "varToSCCIndex: "<< varToSCCIndex<<endl;
    cout << "Domains (remember that the var array is reversed):" <<endl;
    for(SysInt i=0; i<numvars; i++)
    {
        cout << "var:" << i << " vals:" ;
        for(SysInt j=dom_min; j<=dom_max; j++)
        {
            if(var_array[i].inDomain(j))
            {
                cout << j <<", ";
            }
        }
        cout << endl;
    }

    // Check the matching is valid.
    for(SysInt i=0; i<numvars; i++)
    {
        if(!var_array[i].inDomain(varvalmatching[i]))
        {
            cout << "val in matching removed, var: " << i << ", val:" << varvalmatching[i] <<endl;
        }
        for(SysInt j=i+1; j<numvars; j++)
        {
            D_ASSERT(varvalmatching[i]!=varvalmatching[j]);
            D_ASSERT(SCCs[i]!=SCCs[j]);
        }
        D_ASSERT(SCCSplit.isMember(i) == (((char*)SCCSplit2)[i]==1));
        cout << (SysInt)SCCSplit.isMember(i) << ", " << (SysInt) (((char*)SCCSplit2)[i]==1) << endl;
        // The matches correspond.
        D_ASSERT(valvarmatching[varvalmatching[i]-dom_min]==i);
    }

    #ifdef DYNAMICALLDIFF
    // Can't yet check all the watches are in the right place, but can check
    // there are the right number of them on each var.
    for(SysInt i=0; i<(SysInt)var_array.size(); i++)
    {
        SysInt count=0;
        DynamicTrigger* trig=get_dt(i, 0);
        while(trig->queue!=NULL && count<numvals)
        {
            count++;
            if(count<numvals) trig=get_dt(i,count);
        }
        D_ASSERT(count==(SysInt)watches[i].size());
    }
    #endif

    // Check that if an element of the matching is removed, then the var is
    // in to_process.
    for(SysInt i=0; i<(SysInt)var_array.size(); i++)
    {
        if(!var_array[i].inDomain(varvalmatching[i]))
        {
            D_ASSERT(to_process.in(i));
        }
    }
    #endif

    // Call hopcroft for the whole matching.
    if(!matching_wrapper(0, numvars-1))
        return;

    #ifdef DYNAMICALLDIFF
    // sync the watches to the matching,
    // since the matching has just changed.
    #ifndef BTMATCHING
    DynamicTrigger * trig = dynamic_trigger_start();
    for(SysInt j=0; j<numvars; j++)
    {
        var_array[j].addWatchTrigger(trig + j, DomainRemoval, varvalmatching[j]);
    }
    #endif
    #endif

    P("Fixed varvalmatching:" << varvalmatching);

    // now both varvalmatching and valvarmatching contain
    // a complete matching for the SCC.
    // Also, valinlocalmatching contains the domain values
    // used in this SCC.

    #ifndef NO_DEBUG
    // Check the matching is valid.
    for(SysInt i=0; i<numvars; i++)
    {
        D_ASSERT(var_array[i].inDomain(varvalmatching[i]));
        for(SysInt j=i+1; j<numvars; j++)
        {
            D_ASSERT(varvalmatching[i]!=varvalmatching[j]);
            D_ASSERT(SCCs[i]!=SCCs[j]);
        }
        D_ASSERT(SCCSplit.isMember(i) == (((char*)SCCSplit2)[i]==1));
        D_ASSERT(SCCs[varToSCCIndex[i]]==i);
    }
    #endif


    // Call Tarjan's for all vars

    var_indices.clear();
    for(SysInt i=0; i<numvars; i++) var_indices.push_back(i);

    if(numvars>0)
        tarjan_recursive(0);

    return;
  }

  /*
  inline bool greedymatch(SysInt tempvar, SysInt sccindex_start, SysInt sccindex_end)
  {
    D_ASSERT(!var_array[tempvar].inDomain(varvalmatching[tempvar]));

    valinlocalmatching.clear();
    for(SysInt j=sccindex_start; j<=sccindex_end; j++)
    {
        valinlocalmatching.insert(varvalmatching[SCCs[j]]-dom_min);
    }

    for(DomainInt val=var_array[tempvar].getMin(); val<=var_array[tempvar].getMax(); val++)
    {
        if(var_array[tempvar].inDomain(val) && !valinlocalmatching.in(val-dom_min))
        {
            varvalmatching[tempvar]=val;
            valvarmatching[val-dom_min]=tempvar;
            return true;
        }
    }
    return false;
  }

  inline bool greedymatch2(SysInt , SysInt sccindex_start, SysInt sccindex_end)
  {
      // process all the broken matchings.

    valinlocalmatching.clear();
    for(SysInt j=sccindex_start; j<=sccindex_end; j++)
    {
        valinlocalmatching.insert(varvalmatching[SCCs[j]]-dom_min);
    }

    for(SysInt j=sccindex_start; j<=sccindex_end; j++)
    {
        SysInt tempvar=SCCs[j];

        if(var_array[tempvar].inDomain(varvalmatching[tempvar]))
        {
            bool found=false;
            for(DomainInt val=var_array[tempvar].getMin(); val<=var_array[tempvar].getMax(); val++)
            {
                if(var_array[tempvar].inDomain(val) && !valinlocalmatching.in(val-dom_min))
                {
                    varvalmatching[tempvar]=val;
                    valvarmatching[val-dom_min]=tempvar;
                    valinlocalmatching.insert(val-dom_min);
                    found=true;
                    break;
                }
            }
            if(!found)
            {
                return false;
            }
        }
    }

    return true;
  }
  */

  virtual void full_propagate()
  {
      #if UseIncGraph
        {
            // update the adjacency lists. and place dts
            DynamicTrigger* dt=dynamic_trigger_start();
            #ifdef DYNAMICALLDIFF
            dt+=numvars+numvars*numvals;
            #endif
            for(SysInt i=dom_min; i<=dom_max; i++)
            {
                for(SysInt j=0; j<adjlistlength[i-dom_min+numvars]; j++)
                {
                    SysInt var=adjlist[i-dom_min+numvars][j];
                    if(!var_array[var].inDomain(i))
                    {
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
        }
      #endif

      #if UseWatches && defined(CHECKDOMSIZE)
      cout << "Watches and Quimper&Walsh's criterion do not safely co-exist." <<endl;
      FAIL_EXIT();
      #endif

      #if defined(DYNAMICALLDIFF) && !UseWatches
      cout << "watchedalldiff does not work if UseWatches is not defined true." << endl;
      FAIL_EXIT();
      #endif

      // Is this guaranteed to be called before do_prop is ever called??
      // I hope so, because the following test has to be done first.
      if(numvars>numvals)
      {
          getState(stateObj).setFailed(true);
          return;
      }

      // process all variables.
      to_process.clear();    // It seems like this is called twice at the top of the tree, so the clear is necessary.

      for(SysInt i=0; i<numvars; i++)
      {
          to_process.insert(i);
      }

      #ifdef DYNAMICALLDIFF
        // Clear all the BT triggers. This is so that the tests at
        // the start of do_prop will work.
        bt_triggers_start=dynamic_trigger_start()+numvars;
        for(SysInt i=0; i<(SysInt)var_indices.size(); i++)
        {
            SysInt var=var_indices[i];
            for(SysInt j=triggercount[var]; j<numvals; j++)
            {
                if( get_dt(var, j)->queue == NULL)
                {
                    break;
                }
                get_dt(var, j)->sleepDynamicTriggerBT(stateObj);
            }
        }

      // set the variable numbers in the watches.
      DynamicTrigger * trig=dynamic_trigger_start();
      for(SysInt i=0; i<numvars; i++)
      {
          (trig+i)->trigger_info() = i;
      }
      for(SysInt i=0; i<numvars; i++)
      {
          for(SysInt j=0; j<numvals; j++)
          {
              (trig+numvars+(i*numvals)+j)->trigger_info() = i;
          }
      }

      #ifndef BTMATCHING
      // set the watches here so that they start synced to the varvalmatching.
        trig = dynamic_trigger_start();
        for(SysInt j=0; j<numvars; j++)
        {
            var_array[j].addWatchTrigger(trig + j, DomainRemoval, varvalmatching[j]);
            P("Adding watch for var " << j << " val " << varvalmatching[j]);
        }
      #endif
      #endif

      #ifdef SCC
      do_prop();
      #else
      do_prop_noscc();
      #endif
  }

    virtual BOOL check_assignment(DomainInt* v, SysInt array_size)
    {
      D_ASSERT(array_size == (SysInt)var_array.size());
      for(SysInt i=0;i<array_size;i++)
        for( SysInt j=i+1;j<array_size;j++)
          if(v[i]==v[j]) return false;
      return true;
    }

    virtual vector<AnyVarRef> get_vars()
    {
      vector<AnyVarRef> vars;
      vars.reserve(var_array.size());
      for(UnsignedSysInt i = 0; i < var_array.size(); ++i)
        vars.push_back(var_array[i]);
      return vars;
    }


  virtual bool get_satisfying_assignment(box<pair<SysInt,DomainInt> >& assignment)
  {
      bool matchok=true;
      for(SysInt i=0; i<numvars; i++)
      {
          if(!var_array[i].inDomain(varvalmatching[i]))
          {
              matchok=false;
              break;
          }
      }

      if(!matchok)
      {
          if(numvals<numvars) return false; // there can't be a matching.
          #if UseIncGraph
            // update the adjacency lists.
            for(SysInt i=dom_min; i<=dom_max; i++)
            {
                for(SysInt j=0; j<adjlistlength[i-dom_min+numvars]; j++)
                {
                    SysInt var=adjlist[i-dom_min+numvars][j];
                    if(!var_array[var].inDomain(i))
                    {
                        // swap with the last element and remove
                        adjlist_remove(var, i);
                        j--; // stay in the same place, dont' skip over the
                        // value which was just swapped into the current position.
                    }
                }
            }
          #endif

          matchok=bfsmatching(0, numvars-1);
      }

      if(!matchok)
      {
          return false;
      }
      else
      {
          for(SysInt i=0; i<numvars; i++)
          {
              assignment.push_back(make_pair(i, varvalmatching[i]));
          }
          return true;
      }
  }

    // ------------------------------- Targan's algorithm ------------------------------------
    // based on the following pseudocode from wikipedia.
        /*
        Input: Graph G = (V, E), Start node v0

        max_dfs := 0  // Counter for dfs
        U := V        // Collection of unvisited nodes
        S := {}       // An initially empty stack
        tarjan(v0)    // Call the function with the start node

        procedure tarjan(v)
        v.dfs := max_dfs;          // Set the depth index
        v.lowlink := max_dfs;      // v.lowlink <= v.dfs
        max_dfs := max_dfs + 1;    // Increment the counter
        S.push(v);                 // Place v on the stack
        U := U \ {v};              // Separate v from U
        forall (v, v') in E do     // Consider the neighboring nodes
          if (v' in U)
            tarjan(v');            // recursive call
            v.lowlink := min(v.lowlink, v'.lowlink);
          // Ask whether v' is on the stack
          // by a clever constant time method
          // (for example, setting a flag on the node when it is pushed or popped)
          elseif (v' in S)
            v.lowlink := min(v.lowlink, v'.dfs);
          end if
        end for
        if (v.lowlink = v.dfs)     // the root of a strongly connected component
          print "SZK:";
          repeat
            v' := S.pop;
            print v';
          until (v' = v);
        end if
        */

    vector<SysInt> tstack;
    smallset_nolist in_tstack;
    smallset_nolist visited;
    vector<SysInt> dfsnum;
    vector<SysInt> lowlink;

    //vector<SysInt> iterationstack;
    vector<SysInt> curnodestack;

    // Filled in before calling tarjan's.
    bool scc_split;

    SysInt sccindex;

    SysInt max_dfs;

    vector<SysInt> spare_values;
    bool include_sink;
    vector<SysInt> var_indices;  // Should be a pointer so it can be changed.

    smallset sccs_to_process;   // Indices to the first var in the SCC to process.

    SysInt varcount;

    #ifdef DYNAMICALLDIFF
    vector<SysInt> triggercount; // number of triggers on the variable
    DynamicTrigger * bt_triggers_start;   // points at dynamic_trigger_start()+numvars
    #endif

    // An integer represents a vertex, where 0 .. numvars-1 represent the vars,
    // numvars .. numvars+numvals-1 represents the values (val-dom_min+numvars),
    // numvars+numvals is the sink,
    // numvars+numvals+1 is the

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
        #ifdef DYNAMICALLDIFF
        triggercount.resize(numvars);
        #endif
    }

    void tarjan_recursive(SysInt sccindex_start)
    {
        valinlocalmatching.clear();

        DomainInt localmax=var_array[var_indices[0]].getMax();
        DomainInt localmin=var_array[var_indices[0]].getMin();
        valinlocalmatching.insert(varvalmatching[var_indices[0]]-dom_min);

        for(SysInt i=1; i<(SysInt)var_indices.size(); i++)
        {
            SysInt tempvar=var_indices[i];
            DomainInt tempmax=var_array[tempvar].getMax();
            DomainInt tempmin=var_array[tempvar].getMin();
            if(tempmax>localmax) localmax=tempmax;
            if(tempmin<localmin) localmin=tempmin;
            valinlocalmatching.insert(varvalmatching[var_indices[i]]-dom_min);
        }

        if(UseWatches)
        {
            for(SysInt i=0; i<(SysInt)var_indices.size(); i++)
            {
                #if !defined(DYNAMICALLDIFF) || !defined(NO_DEBUG)
                watches[var_indices[i]].clear();
                P("Adding DT for var " << var_indices[i] << " val " << varvalmatching[var_indices[i]]);
                // watch the value from the matching.
                watches[var_indices[i]].insert(varvalmatching[var_indices[i]]-dom_min);
                #endif

                #ifdef DYNAMICALLDIFF
                // Clear all the triggers on this variable. At end now.
                SysInt var=var_indices[i];
                triggercount[var]=1;

                var_array[var].addDynamicTriggerBT(get_dt(var, 0),
                    DomainRemoval, varvalmatching[var]);
                P("Adding DT for var " << var_indices[i] << " val " << varvalmatching[var_indices[i]]);
                #endif
            }
        }

        // spare_values
        // This should be computed somehow on demand because it might not be used.
        // Actually it should be used exactly once.
        #ifdef SPAREVALUESOPT
        if(sparevaluespresent.isMember(sccindex_start))
        {
        #endif
            spare_values.clear();
            for(DomainInt val=localmin; val<=localmax; ++val)
            {
                if(!valinlocalmatching.in(checked_cast<SysInt>(val-dom_min)))
                {
                    for(SysInt j=0; j<(SysInt)var_indices.size(); j++)
                    {
                        if(var_array[var_indices[j]].inDomain(val))
                        {
                            spare_values.push_back(checked_cast<SysInt>(val-dom_min+numvars));
                            break;
                        }
                    }
                }
            }
            //cout << "With spare values "<< spare_values <<endl;

            include_sink= ((SysInt)spare_values.size()>0);  // This should be in the TMS.
        #ifdef SPAREVALUESOPT
            if(!include_sink)
            {
                // Set some bits in sparevaluespresent.
                for(SysInt scci=sccindex_start; ; scci++)
                {
                    D_ASSERT(sparevaluespresent.isMember(scci));
                    sparevaluespresent.remove(scci);
                    if(!SCCSplit.isMember(scci))  // means this is the last element
                    {
                        break;
                    }
                }
            }
        }
        else
        {
            include_sink=false;
        }
        #endif
        // Just generate the spare_values if no empty sets have been seen above
        // in this branch.

        tstack.clear();
        in_tstack.clear();

        visited.clear();
        max_dfs=1;

        scc_split=false;
        sccindex=sccindex_start;

        for(SysInt i=0; i<(SysInt)var_indices.size(); ++i)
        {
            SysInt curnode=var_indices[i];
            if(!visited.in(curnode))
            {
                P("(Re)starting tarjan's algorithm, value:"<< curnode);
                varcount=0;
                visit(curnode, true, sccindex_start);
                P("Returned from tarjan's algorithm.");
            }
        }

        // Clear any extra watches.
        #ifdef DYNAMICALLDIFF
        if(UseWatches)
        {
            for(SysInt i=0; i<(SysInt)var_indices.size(); i++)
            {
                SysInt var=var_indices[i];
                for(SysInt j=triggercount[var]; j<numvals; j++)
                {
                    if( get_dt(var, j)->queue == NULL)
                    {
                        break;
                    }
                    get_dt(var, j)->sleepDynamicTriggerBT(stateObj);
                }
            }
        }
        #endif
    }

    void visit(SysInt curnode, bool toplevel, SysInt sccindex_start)
    {
        tstack.push_back(curnode);
        in_tstack.insert(curnode);
        dfsnum[curnode]=max_dfs;
        lowlink[curnode]=max_dfs;
        max_dfs++;
        visited.insert(curnode);
        //cout << "Visiting node: " <<curnode<<endl;

        if(curnode==numvars+numvals)
        {
            //cout << "Visiting sink node." <<endl;
            D_ASSERT(include_sink);
            // It's the sink so it links to all spare values.

            for(SysInt i=0; i<(SysInt)spare_values.size(); ++i)
            {
                SysInt newnode=spare_values[i];
                //cout << "About to visit spare value: " << newnode-numvars+dom_min <<endl;
                if(!visited.in(newnode))
                {
                    visit(newnode, false, sccindex_start);
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
        else if(curnode<numvars)  // This case should never occur with merge nodes.
        {
            D_ASSERT(find(var_indices.begin(), var_indices.end(), curnode)!=var_indices.end());
            varcount++;
            //cout << "Visiting node variable: "<< curnode<<endl;
            SysInt newnode=varvalmatching[curnode]-dom_min+numvars;
            D_ASSERT(var_array[curnode].inDomain(newnode+dom_min-numvars));

            if(!visited.in(newnode))
            {
                visit(newnode, false, sccindex_start);
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

            D_ASSERT(curnode>=numvars && curnode<(numvars+numvals));
            #ifndef NO_DEBUG
            bool found=false;
            for(SysInt i=0; i<(SysInt)var_indices.size(); i++)
            {
                if(var_array[var_indices[i]].inDomain(curnode+dom_min-numvars))
                {
                    found=true;
                }
            }
            D_ASSERT(found);
            #endif

            SysInt lowlinkvar=-1;
            #if !UseIncGraph
            for(SysInt i=0; i<(SysInt)var_indices.size(); i++)
            {
                SysInt newnode=var_indices[i];
            #else
            for(SysInt i=0; i<adjlistlength[curnode]; i++)
            {
                SysInt newnode=adjlist[curnode][i];
            #endif
                if(varvalmatching[newnode]!=curnode-numvars+dom_min)   // if the value is not in the matching.
                {
                    #if !UseIncGraph
                    if(var_array[newnode].inDomain(curnode+dom_min-numvars))
                    #endif
                    {
                        D_ASSERT(var_array[newnode].inDomain(curnode+dom_min-numvars));
                        //newnode=varvalmatching[newnode]-dom_min+numvars;  // Changed here for merge nodes
                        if(!visited.in(newnode))
                        {
                            // set a watch
                            if(UseWatches)
                            {
                                P("Adding DT for var " << newnode << " val " << curnode-numvars+dom_min);

                                #if !defined(DYNAMICALLDIFF) || !defined(NO_DEBUG)
                                watches[newnode].insert(curnode-numvars);
                                #endif

                                #ifdef DYNAMICALLDIFF
                                var_array[newnode].addDynamicTriggerBT(get_dt(newnode, triggercount[newnode]),
                                    DomainRemoval, curnode-numvars+dom_min);
                                triggercount[newnode]++;
                                #endif
                            }
                            visit(newnode, false, sccindex_start);
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



            // Why the find? Can we not use some DS which is already lying around?
            // And why does it include the whole matching in the find??
            if(include_sink && valinlocalmatching.in(curnode-numvars))
            //    find(varvalmatching.begin(), varvalmatching.end(), curnode+dom_min-numvars)!=varvalmatching.end())
            {
                SysInt newnode=numvars+numvals;
                if(!visited.in(newnode))
                {
                    visit(newnode, false, sccindex_start);
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
            if(UseWatches && lowlinkvar!=-1)
            {
                P("Adding DT for var " << lowlinkvar << " val " << curnode-numvars+dom_min);

                #if !defined(DYNAMICALLDIFF) || !defined(NO_DEBUG)
                watches[lowlinkvar].insert(curnode-numvars);
                #endif
                #ifdef DYNAMICALLDIFF
                var_array[lowlinkvar].addDynamicTriggerBT(get_dt(lowlinkvar, triggercount[lowlinkvar]),
                    DomainRemoval, curnode-numvars+dom_min);
                triggercount[lowlinkvar]++;
                #endif
            }
        }

        //cout << "On way back up, curnode:" << curnode<< ", lowlink:"<<lowlink[curnode]<< ", dfsnum:"<<dfsnum[curnode]<<endl;
        if(lowlink[curnode]==dfsnum[curnode])
        {
            // Did the SCC split?
            // Perhaps we traversed all vars but didn't unroll the recursion right to the top.
            // Then lowlink[curnode]!=1. Or perhaps we didn't traverse all the variables.
            // I think these two cases cover everything.
            if(!toplevel || varcount<(SysInt)var_indices.size())
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
                // For each variable, write it to the scc array.
                // If its the last one, flip the bit.

                varinlocalmatching.clear();  // Borrow this datastructure for a minute.

                P("Writing new SCC:");
                bool containsvars=false;
                for(vector<SysInt>::iterator tstackit=(tstack.end()-1);  ; --tstackit)
                {
                    SysInt copynode=(*tstackit);
                    //cout << "SCC element: "<< copynode<<endl;
                    if(copynode<numvars)
                    {
                        containsvars=true;
                        SCCs[sccindex]=copynode;
                        varToSCCIndex[copynode]=sccindex;
                        sccindex++;
                        //tempset.push_back(copynode);  // need to write into sccs instead.
                        varinlocalmatching.insert(copynode);
                        P("Stored SCC element "<< copynode);
                    }

                    if(copynode==curnode)
                    {
                        // Beware it might be an SCC containing just one value.

                        if(containsvars)
                        {
                            P("Inserting split point.");
                            SCCSplit.remove(sccindex-1);
                            D_DATA(((char*)SCCSplit2)[sccindex-1]=0);
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
                            for(SysInt i=0; i<(SysInt)var_indices.size(); i++)
                            {
                                SysInt curvar=var_indices[i];
                                if(!varinlocalmatching.in(curvar))
                                {
                                    // var not in tempset so might have to do some test against matching.
                                    // Why doing this test? something wrong with the assigned variable optimization?
                                    if(varvalmatching[curvar]!=copynode+dom_min-numvars)
                                    {
                                        P("Removing var: "<< curvar << " val:" << copynode+dom_min-numvars);
                                        if(var_array[curvar].inDomain(copynode+dom_min-numvars))
                                        {
                                            var_array[curvar].removeFromDomain(copynode+dom_min-numvars);
                                            PHALLSETSIZE(var_indices.size()-varinlocalmatching.size());
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


    inline bool matching_wrapper(SysInt sccstart, SysInt sccend)
    {
        #ifdef BFSMATCHING
        return bfs_wrapper(sccstart,sccend);
        #else
        #ifdef NEWHK
        // This is just to test the new HK implementation.
        // to use this, must not be using SCCs and must be using incgraph.
        vector<SysInt> upper;
        upper.resize(numvals, 1);
        vector<SysInt> usage;
        usage.resize(numvals, 0);
        for(SysInt i=0; i<numvars; i++)
        {
            if(varvalmatching[i]!=dom_min-1)
            {
                D_ASSERT(usage[varvalmatching[i]-dom_min]==0);
                usage[varvalmatching[i]-dom_min]=1;
            }
        }
        return hopcroft_wrapper2(SCCs, varvalmatching, upper, usage);
        #else
        return hopcroft_wrapper(sccstart,sccend, SCCs);
        #endif
        #endif
    }

    // BFS alternative to hopcroft. --------------------------------------------

    inline bool bfs_wrapper(SysInt sccstart, SysInt sccend)
    {
        // Call hopcroft for the whole matching.
        if(!bfsmatching(sccstart, sccend))
        {
            // The constraint is unsatisfiable (no matching).
            P("About to fail. Changed varvalmatching: "<< varvalmatching);
            getState(stateObj).setFailed(true);
            return false;
        }

        return true;
    }

    deque<SysInt> fifo;
    vector<SysInt> prev;
    vector<SysInt> matchbac;
    // use push_back to push, front() and pop_front() to pop.

    //Also use invprevious to record which values are matched.
    // (recording val-dom_min)

    inline bool bfsmatching(SysInt sccstart, SysInt sccend)
    {
        // construct the set of matched values.
        invprevious.clear();
        for(SysInt sccindex=sccstart; sccindex<=sccend; sccindex++)
        {
            SysInt var=SCCs[sccindex];
            if(var_array[var].inDomain(varvalmatching[var]))
            {
                invprevious.insert(varvalmatching[var]-dom_min);
            }
        }

        // back up the matching to cover failure
        matchbac=varvalmatching;

        // iterate through the SCC looking for broken matches
        for(SysInt sccindex=sccstart; sccindex<=sccend; sccindex++)
        {
            SysInt startvar=SCCs[sccindex];
            if(!var_array[startvar].inDomain(varvalmatching[startvar]))
            {
                P("Searching for augmenting path for var: " << startvar);
                // Matching edge lost; BFS search for augmenting path to fix it.
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
                    P("Popped vertex " << (curnode<numvars? "(var)":"(val)") << (curnode<numvars? curnode : curnode+dom_min-numvars ));
                    if(curnode<numvars)
                    { // it's a variable
                        // put all corresponding values in the fifo.
                        // Need to check if we are completing an even alternating path.
                        #if !UseIncGraph
                        for(DomainInt val=var_array[curnode].getMin(); val<=var_array[curnode].getMax(); val++)
                        {
                        #else
                        for(SysInt vali=0; vali<adjlistlength[curnode]; vali++)
                        {
                            SysInt val=adjlist[curnode][vali];
                        #endif
                            if(val!=varvalmatching[curnode]
                            #if !UseIncGraph
                                && var_array[curnode].inDomain(val)
                            #endif
                                )
                            {
                                if(!invprevious.in(checked_cast<SysInt>(val-dom_min)))
                                {
                                    // This vertex completes an even alternating path.
                                    // Unwind and apply the path here
                                    P("Found augmenting path:");
                                    SysInt unwindvar=curnode;
                                    DomainInt unwindval=val;
                                    P("unwindvar: "<< unwindvar<<"unwindval: "<< unwindval );
                                    while(true)
                                    {
                                        //invprevious.remove(varvalmatching[unwindvar]-dom_min);
                                        D_ASSERT(var_array[unwindvar].inDomain(unwindval));
                                        D_ASSERT(varvalmatching[unwindvar]!=unwindval);

                                        varvalmatching[unwindvar]=checked_cast<SysInt>(unwindval);
                                        P("Setting var "<< unwindvar << " to "<< unwindval);

                                        if(unwindvar==startvar)
                                        {
                                            break;
                                        }

                                        unwindval=prev[unwindvar];
                                        unwindvar=prev[checked_cast<SysInt>(unwindval-dom_min+numvars)];
                                    }

                                    #ifdef PLONG
                                    cout << "varvalmatching:";
                                    for(SysInt sccindex=sccstart; sccindex<=sccend; sccindex++)
                                    {
                                        if(var_array[SCCs[sccindex]].inDomain(varvalmatching[SCCs[sccindex]]))
                                            cout << SCCs[sccindex] << "->" << varvalmatching[SCCs[sccindex]] << ", ";
                                    }
                                    cout << endl;
                                    #endif

                                    invprevious.clear();  // THIS SHOULD BE CHANGED -- RECOMPUTING THIS EVERY TIME IS STUPID.
                                    for(SysInt sccindex=sccstart; sccindex<=sccend; sccindex++)
                                    {
                                        SysInt var=SCCs[sccindex];
                                        if(var_array[var].inDomain(varvalmatching[var]))
                                        {
                                            invprevious.insert(varvalmatching[var]-dom_min);
                                        }
                                    }

                                    finished=true;
                                    break;  // get out of for loop
                                }
                                else
                                {
                                    if(!visited.in(val-dom_min+numvars))
                                    {
                                        visited.insert(val-dom_min+numvars);
                                        prev[checked_cast<SysInt>(val-dom_min+numvars)]=curnode;
                                        fifo.push_back(checked_cast<SysInt>(val-dom_min+numvars));
                                    }
                                }
                            }
                        } // end for
                    }
                    else
                    { // popped a value from the stack. Follow the edge in the matching.
                        D_ASSERT(curnode>=numvars && curnode < numvars+numvals);
                        DomainInt stackval=curnode+dom_min-numvars;
                        SysInt vartoqueue=-1;
                        D_DATA(bool found=false);
                        #if !UseIncGraph
                        for(SysInt scci=sccstart; scci<=sccend; scci++)
                        {
                            vartoqueue=SCCs[scci];
                        #else
                        for(SysInt vartoqueuei=0; vartoqueuei<adjlistlength[curnode]; vartoqueuei++)
                        {
                            vartoqueue=adjlist[curnode][vartoqueuei];
                        #endif
                            if(varvalmatching[vartoqueue]==stackval
                                #if !UseIncGraph
                                && var_array[vartoqueue].inDomain(stackval)
                                #endif
                                )
                            {
                                D_DATA(found=true);
                                break;
                            }
                        }
                        D_ASSERT(found);  // if this assertion fails, then invprevious must be wrong.
                        if(!visited.in(vartoqueue)) // I think it's impossible for this test to be false.
                        {
                            visited.insert(vartoqueue);
                            prev[checked_cast<SysInt>(vartoqueue)]=checked_cast<SysInt>(stackval);
                            fifo.push_back(vartoqueue);
                        }
                    }
                }
                if(!finished)
                {   // no augmenting path found
                    P("No augmenting path found.");
                    // restore the matching to its state before the algo was called.
                    varvalmatching=matchbac;
                    return false;
                }

            }
        }
        return true;
    }

};  // end of AlldiffGacSlow
