#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 
*/

#include "../constraints/sum_constraints/constraint_weightedsum.h"

template<typename VarArray,  typename VarSum>
AbstractConstraint*
BuildCT_LEQSUM(StateObj* stateObj, const VarArray& _var_array, const vector<VarSum>& _var_sum, ConstraintBlob&)
{ 
  if(_var_array.size() == 2)
  {
    std::array<typename VarArray::value_type, 2> v_array;
    for(SysInt i = 0; i < 2; ++i)
      v_array[i] = _var_array[i];
    return LightLessEqualSumCon(stateObj, v_array, _var_sum[0]);
  }
  else
  {
    return new LessEqualSumConstraint<VarArray, VarSum>(stateObj, _var_array, _var_sum[0]); 
  }
}

inline AbstractConstraint*
BuildCT_LEQSUM(StateObj* stateObj, const vector<BoolVarRef>& var_array, const vector<ConstantVar>& var_sum, ConstraintBlob&)
{ 
  SysInt t2(checked_cast<SysInt>(var_sum[0].getAssignedValue()));
  return BoolLessEqualSumCon(stateObj, var_array, t2);
}
 
template<typename T1, typename T2>
AbstractConstraint*
BuildCT_WEIGHTLEQSUM(StateObj* stateObj, vector<T1> vec, const vector<T2>& t2, ConstraintBlob& b)
{
  vector<DomainInt> scale = b.constants[0];
  // Preprocess to remove any multiplications by 0, both for efficency
  // and correctness
  if(scale.size() != vec.size())
  {
    FAIL_EXIT("In a weighted sum constraint, the vector of weights must have the same length to the vector of variables.");
  }
  for(UnsignedSysInt i = 0; i < scale.size(); ++i)
  {
    if(scale[i] == 0)
    {
      scale.erase(scale.begin() + i);
      vec.erase(vec.begin() + i);
      --i; // So we don't miss an element.
    }
  }

  BOOL multipliers_size_one = true;
  for(UnsignedSysInt i = 0; i < scale.size(); ++i)
  {
    if(scale[i] != 1 && scale[i] != -1)
    {
      multipliers_size_one = false;
      i = scale.size();
    }
  }

  if(multipliers_size_one)
  {
    vector<SwitchNeg<T1> > mult_vars(vec.size());
    for(UnsignedSysInt i = 0; i < vec.size(); ++i)
      mult_vars[i] = SwitchNeg<T1>(vec[i], scale[i]);
    return BuildCT_LEQSUM(stateObj, mult_vars, t2, b);
  }
  else
  {
    vector<MultiplyVar<T1> > mult_vars(vec.size());
    for(UnsignedSysInt i = 0; i < vec.size(); ++i)
      mult_vars[i] = MultiplyVar<T1>(vec[i], scale[i]);
    return BuildCT_LEQSUM(stateObj, mult_vars, t2, b);
  }
}

BUILD_CT(CT_WEIGHTLEQSUM, 2)
