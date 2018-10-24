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

/*
  Chris, note there is a bit where I comment out how I really want to propagate instead
  of calling limit_reached, but I couldn't remember if you told me I could have
  that method or not.    It saves going through the whole clause.
 */

/** @help constraints;watchsumgeq Description
  The constraint watchsumgeq(vec, c) ensures that sum(vec) >= c.
*/

/** @help constraints;watchsumgeq Notes
  For this constraint, small values of c are more efficient.

  Equivalent to litsumgeq(vec, [1,...,1], c), but faster.

  This constraint works on 0/1 variables only.
*/

/** @help constraints;watchsumgeq Reifiability
  This constraint is not reifiable.
*/

/** @help constraints;watchsumgeq References
  See also

  help constraints watchsumleq
  help constraints litsumgeq
*/

/** @help constraints;watchsumleq Description
  The constraint watchsumleq(vec, c) ensures that sum(vec) <= c.
*/

/** @help constraints;watchsumleq Notes
  Equivalent to litsumgeq([vec1,...,vecn], [0,...,0], n-c) but faster.

  This constraint works on binary variables only.

  For this constraint, large values of c are more efficient.
*/

/** @help constraints;watchsumleq References
  See also

  help constraints watchsumgeq
  help constraints litsumgeq
*/

#ifndef CONSTRAINT_DYNAMIC_SUM_H
#define CONSTRAINT_DYNAMIC_SUM_H

// VarToCount = 1 means leq, = 0 means geq.
template<typename VarArray, typename VarSum, SysInt VarToCount = 1, BOOL is_reversed = false >
  struct BoolLessSumConstraintDynamic : public AbstractConstraint
{
  virtual string constraint_name()
    { if(VarToCount) return "watchsumleq"; else return "watchsumgeq"; }

  typedef BoolLessSumConstraintDynamic<VarArray, VarSum,1-VarToCount> NegConstraintType;
  typedef typename VarArray::value_type VarRef;

  CONSTRAINT_ARG_LIST2(var_array, VarToCount ? (DomainInt)(var_array.size()) - var_sum : (DomainInt)(var_sum));

  // When VarToCount=1 this constraint actually counts 0's and ensures there are var_sum or more.
  // Name of the class should really be changed, and VarToCount changed to val.. and values flipped
  // for it to make sense.

  VarArray var_array;
  VarSum var_sum;

  void* unwatched_indexes;
  SysInt last;
  SysInt num_unwatched;

  SysInt& unwatched(SysInt i)
    { return static_cast<SysInt*>(unwatched_indexes)[i]; }

  BoolLessSumConstraintDynamic(StateObj* _stateObj, const VarArray& _var_array, VarSum _var_sum) :
  AbstractConstraint(_stateObj), var_array(_var_array), var_sum(_var_sum), last(0)
  {
    D_ASSERT((VarToCount == 0) || (VarToCount == 1));
#ifndef DYNAMICTRIGGERS
    cerr << "This almost certainly isn't going to work... sorry" << endl;
#endif
  // Sum of 1's is >= K
  // == Number of 1's is >=K         // this is the one I want to do
  // == Number of 0's is <= N-K


    SysInt array_size = var_array.size();

    if (var_sum >= array_size || var_sum < 0)
    {
      // In these cases the constraints are all set before search.
      // This will happen before triggers set up in full_propagate
      // Thus zero triggers are needed
    }
    else
    {
      num_unwatched = checked_cast<SysInt>(array_size - var_sum - 1);
      D_ASSERT(num_unwatched >= 0);
      unwatched_indexes = checked_malloc(sizeof(UnsignedSysInt) * num_unwatched);
    }
  }

  virtual SysInt dynamic_trigger_count()
  {
    if(var_sum >= (SysInt)var_array.size() || var_sum < 0)
      return 0;
    else
      return checked_cast<SysInt>(var_sum + 1);
  }

  virtual void full_propagate()
  {
    DynamicTrigger* dt = dynamic_trigger_start();

    if(var_sum <= 0)
      // Constraint trivially satisfied
      return;

    SysInt array_size = var_array.size();
    DomainInt triggers_wanted = var_sum + 1;
    SysInt index;

    for(index = 0; (index < array_size) && (triggers_wanted > 0); ++index)
    {
      if(var_array[index].inDomain(1 - VarToCount))
      {
    // delay setting up triggers in case we don't need to
        --triggers_wanted;
      }
    }

    D_ASSERT(triggers_wanted >= 0);

    if(triggers_wanted > 1)    // Then we have failed, forget it.
    {
      getState(stateObj).setFailed(true);
      return;
    }
    else if(triggers_wanted == 1)      // Then we can propagate
    {                               // We never even set up triggers
      for(SysInt i = 0; i < array_size; ++i)
      {
        if(var_array[i].inDomain(1 - VarToCount))
        {
          if(VarToCount)
            var_array[i].setMax(0);
          else
            var_array[i].setMin(1);
        }
      }
    }
    else                                // Now set up triggers
    {
      D_ASSERT(triggers_wanted == 0);

      SysInt j = 0;

          // We only look at the elements of vararray that we looked at before
          // Exactly triggers_wanted of them have the val in their domain.

      for(SysInt i = 0; (i < index); ++i)   // remember index was the elts we looked at
      {
        if(var_array[i].inDomain(1 - VarToCount))
        {
          dt->trigger_info() = i;
          var_array[i].addDynamicTrigger(dt, VarToCount ? LowerBound : UpperBound);
          ++dt;
        }
        else
        {
          unwatched(j) = i;
          ++j;
              // When run at root we could optimise as follows
              //    If VarToCount not in domain then do not put j in unwatched
              //      Instead decrement num_unwatched
        }
      }

      for(SysInt i=index; i < array_size; ++i)
      {
        unwatched(j) = i;
        ++j;
      }


      D_ASSERT(j == num_unwatched);
    }
    return;
  }

  /// Checks the consistency of the constraint's data structures
  //
  //  NOT updated for new type of watched data structure
  //
  BOOL check_consistency()
  {
    return true;
  }

  virtual void propagate(DynamicTrigger* dt)
  {
    PROP_INFO_ADDONE(DynSum);
    D_ASSERT(check_consistency());
    SysInt propval = dt->trigger_info();
    D_ASSERT(var_array[propval].getAssignedValue() == VarToCount);
  // should generalise
  // and will need to loop round for watched lits

    bool found_new_support = false;

    SysInt loop;
    SysInt j;

    for(loop = 0 ; (!found_new_support) && loop < num_unwatched ; ++loop )
    {
      D_ASSERT(num_unwatched > 0);

      j = (last+1+loop) % num_unwatched;
      if(var_array[unwatched(j)].inDomain(1 - VarToCount))
      {
        found_new_support = true;
      }
    }

    if (found_new_support)         // so we have found a new literal to watch
    {
      SysInt& unwatched_index = unwatched(j);

      dt->trigger_info() = unwatched_index;
      var_array[unwatched_index].addDynamicTrigger(
        dt,
        VarToCount ? LowerBound : UpperBound);

      unwatched_index = propval;
      last = j;

      return;
    }

  // there is no literal to watch, we need to propagate

    DynamicTrigger* dt2 = dynamic_trigger_start();

    for(SysInt z = 0; z < var_sum + 1; ++z)
    {
      if(dt != dt2)       // that one has just been set the other way
      {
        if(VarToCount)
          var_array[dt2->trigger_info()].setMax(0);
        else
          var_array[dt2->trigger_info()].setMin(1);
      }
      dt2++;
    }
  }

  virtual BOOL check_assignment(DomainInt* v, SysInt v_size)
  {
    D_ASSERT(v_size == (SysInt)var_array.size());
    SysInt count = 0;
    for(SysInt i = 0; i < v_size; ++i)
      count += (v[i] != VarToCount);
    return count >= var_sum;
  }

  virtual vector<AnyVarRef> get_vars()
  {
    vector<AnyVarRef> vars;
    vars.reserve(var_array.size());
    for(UnsignedSysInt i = 0; i < var_array.size(); ++i)
      vars.push_back(AnyVarRef(var_array[i]));
    return vars;
  }

  virtual bool get_satisfying_assignment(box<pair<SysInt,DomainInt> >& assignment)
  {
    if(var_sum <= 0)
    {
      return true;
    }

    SysInt count = 0;
    for(SysInt i = 0; i < (SysInt)var_array.size(); ++i)
    {
      if(var_array[i].inDomain(!VarToCount))
      {
        assignment.push_back(make_pair(i, !VarToCount));
        count++;
        if(count >= var_sum)
          return true;
      }
    }
    // We didn't make a complete assignment
    return false;
  }

  virtual AbstractConstraint* reverse_constraint()
  {
     return new BoolLessSumConstraintDynamic<VarArray, VarSum, 1-VarToCount, true>
               (stateObj, var_array, var_array.size()-var_sum+1);
  }


};

template<typename VarArray,  typename VarSum>
AbstractConstraint*
  BoolLessEqualSumConDynamic(StateObj* stateObj, const VarArray& _var_array,  VarSum _var_sum)
{
  return new BoolLessSumConstraintDynamic<VarArray,VarSum>(stateObj, _var_array,
    (SysInt)_var_array.size() - (SysInt)(_var_sum));
}

template<typename VarArray,  typename VarSum>
AbstractConstraint*
  BoolGreaterEqualSumConDynamic(StateObj* stateObj, const VarArray& _var_array,  VarSum _var_sum)
{
  return new BoolLessSumConstraintDynamic<VarArray,VarSum,0>(stateObj, _var_array, _var_sum);
}


#include "dynamic_sum_sat.h"
#include "dynamic_binary_sat.h"
#include "dynamic_3_sat.h"
#endif
