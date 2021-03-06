----------------------------------------------------------------------
-- Generates unit test data to test Torch to DeepBoof
--
-- Peter Abeles
----------------------------------------------------------------------



require 'torch'
require 'nn'

output_dir = "torch_data"

os.execute("mkdir -p "..output_dir)

torch.setdefaulttensortype("torch.DoubleTensor")
torch.save(paths.concat(output_dir,'Tensor_F64'), torch.randn(2,3,10))
torch.setdefaulttensortype("torch.FloatTensor")
torch.save(paths.concat(output_dir,'Tensor_F32'), torch.randn(2,3,10))
--torch.setdefaulttensortype("torch.ByteTensor")
tmp = torch.randn(2,3,10)*100
torch.save(paths.concat(output_dir,'Tensor_U8'), tmp:byte())


