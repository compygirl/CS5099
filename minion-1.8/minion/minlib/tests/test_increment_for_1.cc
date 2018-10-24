#include "minlib/minlib.hpp"

using namespace std;

int main(void)
{
    vector<vector<int> > vecs;
    for(auto v : ContainerRange(make_vec(2,1,2)))
    {
        vecs.push_back(v);
    }

    D_ASSERT(vecs.size() == 4);
    D_ASSERT(vecs[0] == make_vec(0,0,0));
    D_ASSERT(vecs[1] == make_vec(0,0,1));
    D_ASSERT(vecs[2] == make_vec(1,0,0));
    D_ASSERT(vecs[3] == make_vec(1,0,1));   
}
