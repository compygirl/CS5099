#include "../minion.h"
/* Minion Constraint Solver
   http://minion.sourceforge.net
   
   For Licence Information see file LICENSE.txt 
*/

#include "../constraints/constraint_mddc.h"

template <typename T>
AbstractConstraint*
BuildCT_MDDC(StateObj* stateObj,const T& t1, ConstraintBlob& b)
{ return new MDDC<T>(stateObj, t1, b.tuples); }

BUILD_CT(CT_MDDC, 1)
