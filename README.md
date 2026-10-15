# PAROT_with_GraphDB_KG
NL to SPARQL with multi-hop KGs

Rebuild of https://github.com/onexpeters/PAROT to work with Knowledge graphs hosted on GraphDB

Developed as part of RAWproject.eu 

## Setup
Requires Git LFS for large data files.
1. Install Git LFS: https://git-lfs.github.com
2. Run `git lfs install`
3. Then clone normally

## Configuration
Copy `config.properties.template` to `config.properties` and fill in your GraphDB credentials:
- GraphDB username and password
- GraphDB server IP and port
- Repository name

`config.properties` is gitignored and must never be committed.

## Run a query
see HOW_TO_RUN.md for instructions to run on windows11 

## LICENSE 
Licence This project is licensed under the Creative Commons Attribution–NonCommercial–NoDerivatives 4.0 International (CC BY-NC-ND 4.0) licence. You are free to: Share — copy and redistribute the material in any medium or format Under the following conditions: Attribution (BY) — You must give appropriate credit, provide a link to the licence, and indicate if changes were made. NonCommercial (NC) — You may not use the material for commercial purposes. NoDerivatives (ND) — You may not distribute modified versions of the material. The full licence terms are available at: https://creativecommons.org/licenses/by-nc-nd/4.0/

Copyright © 2026 [Gabriella Rossi]. All rights reserved except where expressly permitted under the CC BY-NC-ND 4.0 licence.

Unless otherwise stated, all files in this repository are covered by the licence above. The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
