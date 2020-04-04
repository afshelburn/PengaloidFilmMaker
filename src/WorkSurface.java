import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.TreeMap;
import java.util.Vector;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.LookAndFeel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicDesktopIconUI;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;

public class WorkSurface {

	final Logger logger = Logger.getLogger(this.getClass().getName());
	
	JFrame mainFrame;
	JDesktopPane desktop;
	JToolBar toolbar;
	
	Map<String, SceneView> sceneViews;
	Map<String, StoryView> storyViews;
	
	Map<Integer, CameraView> cameraViews;
	
	Map<String, Compositor> compositors;
	
	Map<String, ImageEffectPanel> effects;
	
	File workingFolder = null;
	
	//to do, add prompt on exit if modified
	boolean unsaved = false;
	
	Map<Image, ImageIcon> thumbHash = new HashMap<Image, ImageIcon>();
	
	interface ImageSource {
		public Image getImage();
		public void addObserver(Observer o);
		public void deleteObserver(Observer o);
	}

	class Watchable<T extends Object> extends Observable {
		T myObj;
		public void set(T obj) {
			if(obj != myObj) {
				myObj = obj;
				setChanged();
				notifyObservers();
			}
		}
		public T get() {
			return myObj;
		}
	}
	
	/**
	 * Simple panel to display an image
	 * @author antho
	 *
	 */
	class ImagePanel extends ProportionalPanel implements ImageSource {
		
		Watchable<Image> watch = new Watchable<Image>();
		
		public ImagePanel() {
			super(1920, 1080);
		}
		
		public void setImage(Image img) {
			watch.set(img);
			//ImagePanel.this.image = watch.get();
			ImagePanel.this.repaint();
		}
		
		public Image getImage() {
			return watch.get();
		}
		
		public Image cloneImage() {
			if(watch.get() == null)return null;
			Image img = watch.get();
			BufferedImage copyOfImage = 
					   new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB);
					Graphics g = copyOfImage.createGraphics();
					g.drawImage(img, 0, 0, null);
					g.dispose();
			return copyOfImage;
		}
		
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			Image img = watch.get();
			if(img == null)return;
			Dimension d = this.getSize();
			float h = img.getHeight(null);
			float w = img.getWidth(null);
			float ratio = h/w;
			g.drawImage(img, 0, 0, d.width, (int) (((float) d.width)*ratio), null);
		}

		@Override
		public void addObserver(Observer o) {
			watch.addObserver(o);
		}

		@Override
		public void deleteObserver(Observer o) {
			watch.deleteObserver(o);
		}
		
	}
	
	/**
	 * Overlays one or more secondary transparent images on an image panel
	 * @author antho
	 *
	 */
	class OnionPanel extends ImagePanel {
		
		private Vector<Image> onion = new Vector<Image>();
		float alpha = 0.5f;
		
		public OnionPanel() {
			
		}
		
		public void setAlpha(float alpha) {
			this.alpha = alpha;
		}
		
		public void setOnionImage(Image image) {
			this.onion.clear();
			if(image != null) {
				this.onion.add(image);
			}
		}
		
		public void setOnionImages(Collection<Image> images) {
			this.onion.clear();
			this.onion.addAll(images);
		}
		
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			
			Image image = getImage();
			if(image != null) {
				Dimension d = this.getSize();
				float h = image.getHeight(null);
				float w = image.getWidth(null);
				float ratio = h/w;
				g.drawImage(image, 0, 0, d.width, (int) (((float) d.width)*ratio), null);
			}
			
			for(Image layer : onion) {

				int rule = AlphaComposite.SRC_OVER;
				Composite comp = AlphaComposite.getInstance(rule, alpha);
				Graphics2D g2 = (Graphics2D) g;
				g2.setComposite(comp);
				
				Dimension d = this.getSize();
				float h = image.getHeight(null);
				float w = image.getWidth(null);
				float ratio = h/w;
				
				g2.drawImage(layer, 0, 0, d.width, (int) (((float) d.width)*ratio), null);
				
			}
			
		}
	}
	
	/**
	 * Class listens for a connected "camera" sending png images over the supplied socket, allows capturing 
	 * the current image to a scene
	 * @author antho
	 *
	 */
	class CameraView extends JInternalFrame implements Iterable<Image>, ImageSource {
		
		OnionPanel imagePanel;
		int port;
		JComboBox<String> sceneSelector;
		JCheckBox onionCheck;
		JSlider onionAlpha;
		
		public CameraView(int port) {
			super("Camera: " + port, true, false, true, true);
			super.setName("Camera: " + port);
			DefaultComboBoxModel<String> scenes = new DefaultComboBoxModel<String>();
			for(String s : sceneViews.keySet()) {
				scenes.addElement(s);
			}
			this.sceneSelector = new JComboBox<String>(scenes);
			this.port = port;
			this.imagePanel = new OnionPanel();
			JButton capture = new JButton("Capture");
			capture.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					if(sceneSelector.getSelectedItem() == null)return;
					String scene = sceneSelector.getSelectedItem().toString();
					int cursor = sceneViews.get(scene).getCursorPosition();
					if(cursor >= 0)cursor += 1;
					sceneViews.get(scene).addImage(cursor, imagePanel.cloneImage());
				}
			});
			onionCheck = new JCheckBox("Enable Onion");
			onionAlpha = new JSlider();
			onionCheck.setSelected(false);
			onionAlpha.setMinimum(0);
			onionAlpha.setMaximum(100);
			onionAlpha.setValue(50);
			onionAlpha.setEnabled(false);
			
			onionCheck.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					onionAlpha.setEnabled(onionCheck.isSelected());
					setOnion();
				}
			});
			
			JPanel control = new JPanel(new FlowLayout(FlowLayout.CENTER));
			control.add(capture);
			control.add(sceneSelector);
			control.add(onionCheck);
			control.add(onionAlpha);
			JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			sp.setLeftComponent(imagePanel);
			sp.setRightComponent(control);
			sp.setResizeWeight(0.5);
			imagePanel.setPreferredSize(new Dimension(480, 270));
			imagePanel.setMinimumSize(new Dimension(480, 270));
			this.getContentPane().add("Center", sp);
			this.setSize(new Dimension(600, 400));
			startMonitor();
			setVisible(true);
		}
		
		public String toString() {
			return super.getName();
		}
		
		protected void setOnion() {
			if(onionCheck.isSelected()) {
				float alpha = ((float) onionAlpha.getValue()) / 100.0f;
				imagePanel.setAlpha(alpha);
				if(sceneSelector.getSelectedItem() != null) {
					SceneView scene = sceneViews.get(sceneSelector.getSelectedItem());
					if(scene != null) {
						Image f = scene.frames.getSelectedValue();
						if(f != null) {
							imagePanel.setOnionImage(f);
						}else {
							imagePanel.setOnionImage(null);
						}
					}
				}
			}else {
				imagePanel.setOnionImage(null);
			}
			imagePanel.repaint();
		}
		
		public void startMonitor() {
			Runnable r = new Runnable() {
				@Override
				public void run() {
					while(true) {
				        ServerSocket serverSocket = null;
				    	
				        try {
				            serverSocket = new ServerSocket(port);
				        } catch (IOException ex) {
				            System.out.println("Can't setup server on this port number. ");
				        }
				
				        Socket socket = null;
				        InputStream in = null;
				
				        try {
				            socket = serverSocket.accept();
				        } catch (IOException ex) {
				            System.out.println("Can't accept client connection. ");
				        }
				
				        try {
				            in = socket.getInputStream();
				        } catch (IOException ex) {
				            System.out.println("Can't get socket input stream. ");
				        }
				        
				        
						try {
							BufferedImage image = ImageIO.read(in);//PNGDecoder.decode(in);
							in.close();
							socket.close();
							serverSocket.close();
							SwingUtilities.invokeLater(new Runnable() {
								@Override
								public void run() {
									imagePanel.setImage(image);
									setOnion();
								}
							});
						} catch (IOException e2) {
							logger.info(e2.getMessage());
						}
					}
				}
				
			};
			new Thread(r).start();
		}

		@Override
		public Iterator<Image> iterator() {
			return new Iterator<Image>() {

				@Override
				public boolean hasNext() {
					return true;
				}

				@Override
				public Image next() {
					return imagePanel.getImage();
				}
				
			};
		}

		@Override
		public Image getImage() {
			return imagePanel.getImage();
		}

		@Override
		public void addObserver(Observer o) {
			imagePanel.addObserver(o);
		}

		@Override
		public void deleteObserver(Observer o) {
			imagePanel.deleteObserver(o);
		}

	}
	
	/**
	 * Simple renderer for showing thumbnails in components, uses a hashmap to cache thumbs in use
	 * @author antho
	 *
	 */
	public class ImageRenderer extends DefaultListCellRenderer {
		
		public ImageRenderer() {
			super();
		}
		
	    @Override
	    public Component getListCellRendererComponent(JList<?> list, Object object, int index,
	        boolean isSelected, boolean cellHasFocus) {
	        
	    	if(object == null)return null;
	    	
	    	Component c = super.getListCellRendererComponent(list, object, index, isSelected, cellHasFocus);
	    	
	    	Image img = null;
	    	
	    	if(object instanceof String) {
	    		if(sceneViews.containsKey(object)) {
		    		if(!sceneViews.get(object).listModel.isEmpty()) {
		    			img = sceneViews.get(object).listModel.firstElement();
		    		}
	    		}
	    	}else if(object instanceof Image) {
	    		img = (Image) object;
	    	}
	    	
	    	if(img == null)return c;
	    	
	    	JLabel l = (JLabel) c;
	    	ImageIcon imageIcon = thumbHash.get(img);
	    	if(imageIcon == null) {
	    		Image ii = img;
	    		float ratio = ((float) ii.getWidth(null)) / ((float) ii.getHeight(null));
	    		Image imageClone = ii.getScaledInstance(128, (int) (128.0f/ratio), Image.SCALE_SMOOTH);
	    		imageIcon = new ImageIcon(imageClone);
	    		thumbHash.put(ii, imageIcon);
	    	}
	         
	        l.setIcon(imageIcon);
	        l.setText("" + index);
	        l. setVerticalTextPosition(JLabel.BOTTOM);
	        l.setHorizontalTextPosition(JLabel.CENTER);
	         
	        return l;
	    }
	     
	}
	
	/**
	 * A named collection of images
	 * @author antho
	 *
	 */
	class SceneView extends JInternalFrame implements Iterable<Image>, ImageSource {
		
		ImagePanel currentFrame;
		JLabel currentFrameLabel;
		JList<Image> frames;
		DefaultListModel<Image> listModel;
		JComboBox<String> storySelector;
		
		public SceneView(String name) {
			super(name, true, false, true, true);
			super.setName(name);
			currentFrameLabel = new JLabel("Frame");
			currentFrame = new ImagePanel();
			currentFrame.setPreferredSize(new Dimension(500,300));
			listModel = new DefaultListModel<Image>();
			DefaultComboBoxModel<String> stories = new DefaultComboBoxModel<String>();
			for(String s : storyViews.keySet()) {
				stories.addElement(s);
			}
			storySelector = new JComboBox<String>(stories);
			frames = new JList<Image>(listModel);
			frames.setCellRenderer(new ImageRenderer());
			frames.setLayoutOrientation(JList.HORIZONTAL_WRAP);
			frames.setVisibleRowCount(-1);
			frames.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			frames.addListSelectionListener(new ListSelectionListener() {
				@Override
				public void valueChanged(ListSelectionEvent arg0) {
					currentFrame.setImage(frames.getSelectedValue());
					currentFrameLabel.setText("Frame " + frames.getSelectedIndex());
				}
			});
			
			JButton moveLeft = new JButton("<<--");
			moveLeft.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(frames.getSelectedIndex() <= 0)return;
					int sel = frames.getSelectedIndex();
					//remove the item to the left
					Image removed = listModel.remove(sel-1);
					listModel.add(sel, removed);
				}
			});
			
			JButton moveRight = new JButton("-->>");
			moveRight.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(frames.getSelectedIndex() >= listModel.size()-1)return;
					if(frames.getSelectedIndex() < 0)return;
					int sel = frames.getSelectedIndex();
					//remove the item to the right
					Image removed = listModel.remove(sel+1);
					listModel.add(sel, removed);
				}
			});
			
			JButton delete = new JButton("Delete");
			delete.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(frames.getSelectedIndex() >= listModel.size())return;
					if(frames.getSelectedIndex() < 0)return;
					int sel = frames.getSelectedIndex();
					listModel.remove(sel);
				}
			});
			
			JButton copy = new JButton("Duplicate");
			copy.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(frames.getSelectedValue() == null)return;
					if(currentFrame.getImage() == null)return;
					Image cln = currentFrame.cloneImage();
					int sel = frames.getSelectedIndex();
					//remove the item to the right
					listModel.add(sel, cln);
				}
			});
			
			JButton rename = new JButton("Rename");
			rename.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					String name = SceneView.this.getName();
					String newName = JOptionPane.showInputDialog((Component) ae.getSource(), "Name:", name);
					if(newName != null) {
						if(sceneViews.containsKey(newName)) {
							JOptionPane.showMessageDialog((Component) ae.getSource(), "Name already in use");
						}else {
							SceneView.this.rename(newName);
						}
					}
				}
			});
			
			JButton copyScene = new JButton("Duplicate Scene");
			copyScene.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					String sceneName = "Scene " + (sceneViews.size()+1);
					SceneView sv = WorkSurface.this.addScene(sceneName);
					Enumeration<Image> imgs = listModel.elements();
					while(imgs.hasMoreElements()) {
						sv.addImage(-1, imgs.nextElement());
					}
				}
			});
			
			JButton reverseOrder = new JButton("Reverse Scene");
			reverseOrder.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					ArrayList<Image> list = new ArrayList<Image>();
					Enumeration<Image> imgs = listModel.elements();
					while(imgs.hasMoreElements()) {
						list.add(imgs.nextElement());
					}
					Collections.reverse(list);
					listModel.removeAllElements();
					for(Image img : list) {
						SceneView.this.addImage(-1, img);
					}
				}
			});
			
			//to do, make setting
			int frameRate = 100;
			JButton play = new JButton("Play");
			play.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					Runnable r = new Runnable() {
						@Override
						public void run() {
							int startIndex = frames.getSelectedIndex();
							if(startIndex < 0)startIndex = 0;
							for(int i = startIndex; i < listModel.getSize() + 1; i++) {
								final int c = i == listModel.getSize() ? startIndex : i;
								SwingUtilities.invokeLater(new Runnable() {
									@Override
									public void run() {
										frames.setSelectedIndex(c);
									}
								});
								try {
									Thread.sleep(frameRate);
								} catch (InterruptedException e) {}
							}
						}
					};
					new Thread(r).start();
				}
			});
			
			JPanel control = new JPanel(new FlowLayout(FlowLayout.CENTER));
			control.add(moveLeft);
			control.add(play);
			control.add(copy);
			control.add(rename);
			control.add(delete);
			control.add(moveRight);
			
			JButton capture = new JButton("Add to Story");
			capture.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					String story = storySelector.getSelectedItem().toString();
					if(storyViews.containsKey(story)) {
						int cursor = storyViews.get(story).getCursorPosition();
						storyViews.get(story).addScene(cursor, SceneView.this.getName());
					}
				}
			});
			JPanel capturePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
			capturePanel.add(capture);
			capturePanel.add(storySelector);
			capturePanel.add(copyScene);
			capturePanel.add(reverseOrder);
			JPanel center = new JPanel(new BorderLayout());
			center.add("Center", currentFrame);
			center.add("South", capturePanel);
			JPanel main = new JPanel(new BorderLayout());
			main.add("Center", center);
			main.add("South", control);
			main.add("North", currentFrameLabel);
			JScrollPane sp = new JScrollPane(frames);
			
			JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			split.setLeftComponent(main);
			split.setRightComponent(sp);
			split.setResizeWeight(0.5);
			currentFrame.setPreferredSize(new Dimension(480, 270));
			currentFrame.setMinimumSize(new Dimension(480, 270));

			SceneView.this.getContentPane().add("Center", split);
			
			SceneView.this.setTitle(name);
		}
		
		public String toString() {
			return super.getName();
		}
		
		public void rename(String newName) {
			String currentName = super.getName();
			SceneView.this.setName(newName);
			SceneView.this.setTitle(newName);
			sceneViews.put(newName, this);
			for(StoryView view : storyViews.values()) {
				for(int i = 0; i < view.listModel.getSize(); i++) {
					if(view.listModel.getElementAt(i).contentEquals(currentName)) {
						view.listModel.setElementAt(newName, i);
					}
				}
			}
			sceneViews.remove(currentName);
			
			for(CameraView cv : cameraViews.values()) {
				DefaultComboBoxModel<String> scenes = (DefaultComboBoxModel<String>) cv.sceneSelector.getModel();
				int currentIndex = scenes.getIndexOf(currentName);
				scenes.removeElement(currentName);
				scenes.insertElementAt(newName, currentIndex);
			}
		}
		
		public int getCursorPosition() {
			return frames.getSelectedIndex();
		}
		
		public void addImage(int index, Image image) {
			if(image == null)return;
			if(index < 0 || index > listModel.getSize()) {
				index = listModel.getSize();
			}
			listModel.add(index, image);
			currentFrame.setImage(image);
			frames.setSelectedValue(image, true);
		}
		
		public void store(String key, Properties properties) {
			String dirName = sanitizeString(key);
			File dir = new File(workingFolder, dirName);
			if(!dir.exists()) {
				if(!dir.mkdirs()) {
					logger.warning("Unable to create scene directory: " + dir.getAbsolutePath());
				}
			}
			Vector<String> names = new Vector<String>();
			for(int index = 0; index < listModel.getSize(); index++) {
				String name = "frame_" + index + ".png";
				WorkSurface.this.storeImage(new File(dir, name), listModel.elementAt(index));
				names.add(name);
			}
			properties.setProperty(key, String.join(",", names));
		}
		
		public void restore(String s, Properties properties) {
			String dirName = sanitizeString(s);
			File dir = new File(workingFolder, dirName);
			if(!dir.exists()) {
				logger.warning(dir.getAbsolutePath() + " does not exist");
				return;
			}
			String imageList = properties.getProperty(s, null);
			if(imageList != null) {
				String[] fileNames = imageList.split(",");
				for(String fn : fileNames) {
					File f = new File(dir, fn);
					BufferedImage image = null;
					try {
						image = ImageIO.read(f);
					} catch (IOException e) {
						logger.warning("Problem reading " + f.getAbsolutePath() + ": " + e.getMessage());
					}
					if(image != null) {
						SceneView.this.addImage(-1, image);
					}
				}
			}
		}

		@Override
		public Iterator<Image> iterator() {
			return new Iterator<Image>() {
				int pos = 0;
				@Override
				public boolean hasNext() {
					return pos < listModel.getSize() - 1;
				}
				@Override
				public Image next() {
					return listModel.get(pos++);
				}
			};
		}

		@Override
		public Image getImage() {
			return currentFrame.getImage();
		}

		@Override
		public void addObserver(Observer o) {
			currentFrame.addObserver(o);
		}

		@Override
		public void deleteObserver(Observer o) {
			currentFrame.deleteObserver(o);
		}
		
	}
	
	/**
	 * A sequence of scenes defines a story.  Scenes can be used more than once.
	 * @author antho
	 *
	 */
	class StoryView extends JInternalFrame implements Iterable<Image>, ImageSource {
		
		ImagePanel currentFrame;
		JLabel currentFrameLabel;
		JList<String> sceneList;
		String storyName;
		DefaultListModel<String> listModel;
		
		public StoryView(String name) {
			super(name, true, false, true, true);
			super.setName(name);
			currentFrameLabel = new JLabel("Frame");
			this.storyName = name;
			currentFrame = new ImagePanel();
			currentFrame.setPreferredSize(new Dimension(500,300));
			listModel = new DefaultListModel<String>();
			sceneList = new JList<String>(listModel);
			sceneList.setCellRenderer(new ImageRenderer());
			sceneList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
			sceneList.setVisibleRowCount(-1);
			sceneList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
			sceneList.addListSelectionListener(new ListSelectionListener() {
				@Override
				public void valueChanged(ListSelectionEvent arg0) {
					String scene = sceneList.getSelectedValue();
					if(scene == null)return;
					if(!sceneViews.containsKey(scene))return;
					SceneView sv = sceneViews.get(scene);
					Image img = sv.listModel.firstElement();
					if(img == null)return;
					currentFrame.setImage(img);
					currentFrameLabel.setText(scene + " frame 0");
				}
			});
			
			JButton moveLeft = new JButton("<<--");
			moveLeft.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(sceneList.getSelectedIndex() <= 0)return;
					int sel = sceneList.getSelectedIndex();
					//remove the item to the left
					String removed = listModel.remove(sel-1);
					listModel.add(sel, removed);
				}
			});
			JButton moveRight = new JButton("-->>");
			moveRight.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(sceneList.getSelectedIndex() >= listModel.size()-1)return;
					if(sceneList.getSelectedIndex() < 0)return;
					int sel = sceneList.getSelectedIndex();
					//remove the item to the right
					String removed = listModel.remove(sel+1);
					listModel.add(sel, removed);
				}
			});
			
			JButton delete = new JButton("Delete");
			delete.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(sceneList.getSelectedIndex() >= listModel.size())return;
					if(sceneList.getSelectedIndex() < 0)return;
					int sel = sceneList.getSelectedIndex();
					//remove the item to the right
					listModel.remove(sel);
				}
			});
			
			JButton copy = new JButton("Duplicate");
			copy.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					if(sceneList.getSelectedValue() == null)return;
					//if(currentFrame.getImage() == null)return;
					String cln = sceneList.getSelectedValue();
					int sel = sceneList.getSelectedIndex();
					//remove the item to the right
					listModel.add(sel, cln);
				}
			});
			
			JButton rename = new JButton("Rename");
			rename.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					String name = StoryView.this.getName();
					String newName = JOptionPane.showInputDialog((Component) ae.getSource(), "Name:", name);
					if(newName != null) {
						if(newName.contentEquals(name))return;
						if(storyViews.containsKey(newName)) {
							JOptionPane.showMessageDialog((Component) ae.getSource(), "Name already in use");
						}else {
							StoryView.this.rename(newName);
						}
					}
				}
			});
			
			//to do, make setting
			int frameRate = 100;
			JButton play = new JButton("Play");
			play.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent ae) {
					Runnable r = new Runnable() {
						@Override
						public void run() {
							int startIndex = sceneList.getSelectedIndex();
							if(startIndex < 0)startIndex = 0;
							for(int i = startIndex; i < listModel.getSize() + 1; i++) {
								final int c = i == listModel.getSize() ? startIndex : i;
								SwingUtilities.invokeLater(new Runnable() {
									@Override
									public void run() {
										sceneList.setSelectedIndex(c);
									}
								});
								if(i == listModel.getSize())break;
								String scene = listModel.get(c);
								if(sceneViews.containsKey(scene)) {
									for(int j = 0; j < sceneViews.get(scene).listModel.getSize(); j++) {
										Image image = sceneViews.get(scene).listModel.elementAt(j);
										int index = j;
										SwingUtilities.invokeLater(new Runnable() {
											@Override
											public void run() {
												currentFrame.setImage(image);
												currentFrameLabel.setText(scene + " frame: " + index);
											}
										});
										try {
											Thread.sleep(frameRate);
										} catch (InterruptedException e) {}
									}
								}
							}
						}
					};
					new Thread(r).start();
				}
			});
			
			JPanel control = new JPanel(new FlowLayout(FlowLayout.CENTER));
			control.add(rename);
			control.add(moveLeft);
			control.add(play);
			control.add(copy);
			control.add(delete);
			control.add(moveRight);
			JPanel main = new JPanel(new BorderLayout());
			main.add("Center", currentFrame);
			main.add("South", control);
			main.add("North", currentFrameLabel);
			JScrollPane sp = new JScrollPane(sceneList);
			
			JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
			split.setLeftComponent(main);
			split.setRightComponent(sp);
			split.setResizeWeight(0.5);
			currentFrame.setPreferredSize(new Dimension(480, 270));
			currentFrame.setMinimumSize(new Dimension(480, 270));

			StoryView.this.getContentPane().add("Center", split);
			
			StoryView.this.setTitle(storyName);
			
		}
		
		public String toString() {
			return super.getName();
		}
		
		public int getFrameCount() {
			int total = 0;
			for(int i = 0; i < listModel.getSize(); i++) {
				SceneView sv = sceneViews.get(listModel.getElementAt(i));
				if(sv == null)continue;
				total += sv.listModel.getSize();
			}
			return total;
		}
		
		public Image getFrame(int index) {
			int total = 0;
			for(int i = 0; i < listModel.getSize(); i++) {
				SceneView sv = sceneViews.get(listModel.getElementAt(i));
				if(sv == null)continue;
				total += sv.listModel.getSize();
				if(index < total) {
					total -= sv.listModel.getSize();
					int f = index - total;
					return sv.listModel.getElementAt(f);
				}
			}
			return null;
		}
		
		public void rename(String newName) {
			String currentName = super.getName();
			StoryView.this.setName(newName);
			StoryView.this.setTitle(newName);
			storyViews.put(newName, this);
			storyViews.remove(currentName);
			for(SceneView sv : sceneViews.values()) {
				DefaultComboBoxModel<String> stories = (DefaultComboBoxModel<String>) sv.storySelector.getModel();
				int currentIndex = stories.getIndexOf(currentName);
				stories.removeElement(currentName);
				stories.insertElementAt(newName, currentIndex);
			}
		}
		
		public int getCursorPosition() {
			return sceneList.getSelectedIndex();
		}
		
		public void addScene(int index, String scene) {
			if(index < 0 || index > listModel.getSize()) {
				index = listModel.getSize();
			}
			listModel.add(index, scene);
		}

		public void store(String key, Properties properties) {
			String[] myArray = new String[listModel.getSize()];
			for(int i = 0; i < myArray.length; i++) {
				myArray[i] = listModel.getElementAt(i);
			}
			properties.setProperty(key, String.join(",", myArray));
		}
		
		public void restore(String s, Properties properties) {
			String sceneList = properties.getProperty(s, null);
			if(sceneList != null) {
				String[] sceneArray = sceneList.split(",");
				for(String scn : sceneArray) {
					this.addScene(-1, scn);
				}
			}
		}

		@Override
		public Iterator<Image> iterator() {
			return new Iterator<Image>() {
				int pos = 0;
				@Override
				public boolean hasNext() {
					return pos < getFrameCount();
				}
				@Override
				public Image next() {
					return getFrame(pos++);
				}
			};
		}

		@Override
		public Image getImage() {
			return currentFrame.getImage();
		}

		@Override
		public void addObserver(Observer o) {
			currentFrame.addObserver(o);
		}

		@Override
		public void deleteObserver(Observer o) {
			currentFrame.deleteObserver(o);
		}
		
	}
	
	class Compositor extends JInternalFrame implements ImageSource, Observer {
		
		OnionPanel onionPanel;
		
		ImagePanel foreground;
		ImagePanel background;
		ImagePanel greenScreen;
		
		JComboBox<ImageSource> foregroundSelector;
		JComboBox<ImageSource> backgroundSelector;
		JColorChooser colorPicker;
		
		DefaultComboBoxModel<ImageSource> foregroundSources;
		DefaultComboBoxModel<ImageSource> backgroundSources;
		
		JComboBox<String> sceneSelector;
		
		
		JButton colorButton;
		
		JSlider threshold;
		
		Color bgColor = Color.green;
		
		public Compositor(String name) {
			super(name, true, false, true, true);
			super.setName(name);
			foregroundSources = new DefaultComboBoxModel<ImageSource>();
			backgroundSources = new DefaultComboBoxModel<ImageSource>();
			foregroundSelector = new JComboBox<ImageSource>(foregroundSources);
			backgroundSelector = new JComboBox<ImageSource>(backgroundSources);
			onionPanel = new OnionPanel();
			foreground = new ImagePanel();
			background = new ImagePanel();
			greenScreen = new ImagePanel();
			colorButton = new JButton("Pick Background Color");
			colorButton.addActionListener(new ActionListener() {

				@Override
				public void actionPerformed(ActionEvent arg0) {
					Color c = JColorChooser.showDialog((Component) arg0.getSource(), "Pick Background", bgColor);
					if(c != null) {
						setBGColor(c);
						updateSelection();
					}
				}
				
			});
			
			DefaultComboBoxModel<String> scenes = new DefaultComboBoxModel<String>();
			for(String s : sceneViews.keySet()) {
				scenes.addElement(s);
			}
			sceneSelector = new JComboBox<String>(scenes);
			
			JButton capture = new JButton("Capture");
			capture.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					if(sceneSelector.getSelectedItem() == null)return;
					String scene = sceneSelector.getSelectedItem().toString();
					int cursor = sceneViews.get(scene).getCursorPosition();
					if(cursor >= 0)cursor += 1;
					sceneViews.get(scene).addImage(cursor, onionPanel.cloneImage());
				}
			});
			
			threshold = new JSlider();
			threshold.setMinimum(0);
			threshold.setMaximum(255);
			threshold.setValue(20);
			threshold.addChangeListener(new ChangeListener() {

				@Override
				public void stateChanged(ChangeEvent arg0) {
					Compositor.this.updateSelection();
				}
				
			});
			onionPanel.setPreferredSize(new Dimension(480, 270));
			JPanel control = new JPanel(new FlowLayout(FlowLayout.CENTER));
			control.add(foregroundSelector);
			control.add(backgroundSelector);
			control.add(colorButton);
			control.add(threshold);
			control.add(sceneSelector);
			control.add(capture);
			JPanel main = new JPanel(new BorderLayout());
			main.add("South", control);
			main.add("Center", onionPanel);
			this.getContentPane().add("Center", main);
			updateSources();
			foregroundSelector.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					updateSelection();
				}
			});
			backgroundSelector.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					updateSelection();
				}
			});
			JPanel top = new JPanel(new FlowLayout(FlowLayout.CENTER));
			top.add(foreground);
			top.add(greenScreen);
			top.add(background);
			foreground.setPreferredSize(new Dimension(240*3/2, 135*3/2));
			background.setPreferredSize(new Dimension(240*3/2, 135*3/2));
			greenScreen.setPreferredSize(new Dimension(240*3/2, 135*3/2));
			
			foreground.addMouseListener(new MouseAdapter() {

				@Override
				public void mouseClicked(MouseEvent e) {
					Point p = e.getPoint();
					BufferedImage bi = (BufferedImage) foreground.getImage();
					float fracX = ((float) p.getX()) / (float) foreground.getWidth();
					float fracY = ((float) p.getY()) / (float) foreground.getHeight();
					float x = fracX*((float) bi.getWidth());
					float y = fracY*((float) bi.getHeight());
					Color c = new Color(bi.getRGB((int) x, (int) y));
					setBGColor(c);
					updateSelection();
				}
				
			});
			main.add("North", top);
			this.setTitle(name);
		}
		
		public void setBGColor(Color c) {
			this.bgColor = c;
			colorButton.setBackground(this.bgColor);
		}
		
		public String toString() {
			return super.getName();
		}
		
		public void updateSources() {
			
			for(int i = 0; i < foregroundSources.getSize(); i++) {
				ImageSource imgSrc = foregroundSources.getElementAt(i);
				imgSrc.deleteObserver(this);
			}
			
			for(int i = 0; i < backgroundSources.getSize(); i++) {
				ImageSource imgSrc = backgroundSources.getElementAt(i);
				imgSrc.deleteObserver(this);
			}
			
			foregroundSources.removeAllElements();
			backgroundSources.removeAllElements();
			DefaultComboBoxModel<String> scenes = (DefaultComboBoxModel<String>) sceneSelector.getModel();
			scenes.removeAllElements();
			for(SceneView sv : sceneViews.values()) {
				foregroundSources.addElement(sv);
				backgroundSources.addElement(sv);
				scenes.addElement(sv.getName());
			}
			for(StoryView sv : storyViews.values()) {
				foregroundSources.addElement(sv);
				backgroundSources.addElement(sv);
			}
			for(CameraView cv : cameraViews.values()) {
				foregroundSources.addElement(cv);
				backgroundSources.addElement(cv);
			}
			for(Compositor cv : compositors.values()) {
				if(cv != this) {
					foregroundSources.addElement(cv);
					backgroundSources.addElement(cv);
				}
			}
			for(ImageEffectPanel cv : effects.values()) {
				foregroundSources.addElement(cv);
				backgroundSources.addElement(cv);
			}
			
			for(int i = 0; i < foregroundSources.getSize(); i++) {
				ImageSource imgSrc = foregroundSources.getElementAt(i);
				imgSrc.addObserver(this);
			}
			
			for(int i = 0; i < backgroundSources.getSize(); i++) {
				ImageSource imgSrc = backgroundSources.getElementAt(i);
				imgSrc.addObserver(this);
			}
			
			foregroundSelector.revalidate();
			backgroundSelector.revalidate();
			sceneSelector.revalidate();
		}
		
		public void updateSelection() {
			ImageSource fg = (ImageSource) foregroundSelector.getSelectedItem();
			if(fg != null) {
				foreground.setImage(fg.getImage());
				Image clnImg = cloneImage(fg.getImage());
				if(clnImg != null) {
					BufferedImage blank = new BufferedImage(clnImg.getWidth(null), clnImg.getHeight(null), BufferedImage.TYPE_INT_RGB);
					for(int i = 0; i < blank.getWidth(); i++) {
						for(int j = 0; j < blank.getHeight(); j++) {
							blank.setRGB(i, j, Color.green.getRGB());
						}
					}
					Image maskedImage = null;
					try {
						maskedImage = WorkSurface.this.replaceBackground(fg.getImage(), blank, bgColor, threshold.getValue());
					} catch (Exception e) {
						logger.warning(e.getMessage());
					}
					if(maskedImage != null) {
						//foreground.setImage(maskedImage);
						greenScreen.setImage(maskedImage);
					}
				}
			}
			ImageSource bg = (ImageSource) backgroundSelector.getSelectedItem();
			if(bg != null) {
				background.setImage(bg.getImage());
				Image combinedImage = null;
				try {
					combinedImage = WorkSurface.this.replaceBackground(fg.getImage(), (BufferedImage) bg.getImage(), bgColor, threshold.getValue());
				} catch (Exception e) {
					logger.warning(e.getMessage());
				}
				onionPanel.setImage(combinedImage);
			}
		}

		@Override
		public Image getImage() {
			return onionPanel.getImage();
		}

		@Override
		public void addObserver(Observer o) {
			onionPanel.addObserver(o);
		}

		@Override
		public void deleteObserver(Observer o) {
			onionPanel.deleteObserver(o);
		}

		@Override
		public void update(Observable arg0, Object arg1) {
			this.updateSelection();
		}
		
	}
	
	class ImageEffectPanel extends JInternalFrame implements ImageSource, Observer {

		ImagePanel original;
		
		ImagePanel effect;
		
		JComboBox<ImageSource> sourceSelector;
		DefaultComboBoxModel<ImageSource> sources;
		JComboBox<String> sceneSelector;
		
		public ImageEffectPanel(String name) {
			super(name, true, false, true, true);
			super.setName(name);
			
			original = new ImagePanel();
			effect = new ImagePanel();
			
			original.setPreferredSize(new Dimension(480, 270));
			original.setMinimumSize(new Dimension(240, 135));
			
			effect.setPreferredSize(new Dimension(480, 270));
			effect.setMinimumSize(new Dimension(240, 135));
			
			sources = new DefaultComboBoxModel<ImageSource>();
			sourceSelector = new JComboBox<ImageSource>(sources);
			
			sceneSelector = new JComboBox<String>(new DefaultComboBoxModel<String>());
			
			JButton captureButton = new JButton("Capture");
			captureButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					if(sceneSelector.getSelectedItem() == null)return;
					String scene = sceneSelector.getSelectedItem().toString();
					int cursor = sceneViews.get(scene).getCursorPosition();
					if(cursor >= 0)cursor += 1;
					sceneViews.get(scene).addImage(cursor, effect.cloneImage());
				}
			});
			JPanel control = new JPanel(new FlowLayout(FlowLayout.CENTER));
			control.add(sourceSelector);
			control.add(captureButton);
			control.add(sceneSelector);
			JPanel main = new JPanel(new BorderLayout());
			JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER));
			center.add(original);
			center.add(effect);
			main.add("Center", center);
			main.add("South", control);
			this.getContentPane().add("Center", main);
			updateSources();
			updateImage();
		}
		
		public void updateSources() {
			
			ImageSource sel = (ImageSource) sourceSelector.getSelectedItem();
			
			for(int i = 0; i < sources.getSize(); i++) {
				ImageSource imgSrc = sources.getElementAt(i);
				imgSrc.deleteObserver(this);
			}
			
			sources.removeAllElements();
			
			DefaultComboBoxModel<String> scenes = (DefaultComboBoxModel<String>) sceneSelector.getModel();
			scenes.removeAllElements();
			for(SceneView sv : sceneViews.values()) {
				sources.addElement(sv);
				scenes.addElement(sv.getName());
			}
			for(StoryView sv : storyViews.values()) {
				sources.addElement(sv);
			}
			for(CameraView cv : cameraViews.values()) {
				sources.addElement(cv);
			}
			for(Compositor cv : compositors.values()) {
				sources.addElement(cv);
			}
			for(ImageEffectPanel cv : effects.values()) {
				if(cv != this) {
					sources.addElement(cv);
				}
			}
			
			for(int i = 0; i < sources.getSize(); i++) {
				ImageSource imgSrc = sources.getElementAt(i);
				imgSrc.addObserver(this);
			}
			
			if(sel != null) {
				if(sources.getIndexOf(sel) > -1) {
					sourceSelector.setSelectedItem(sel);
				}
			}
			
			sourceSelector.revalidate();
			sceneSelector.revalidate();
			
			updateImage();
		}
		
		@Override
		public void update(Observable arg0, Object arg1) {
			updateSources();
		}
		
		public void updateImage() {
			ImageSource fg = (ImageSource) sourceSelector.getSelectedItem();
			if(fg != null) {
				original.setImage(fg.getImage());
				Image clnImg = cloneImage(fg.getImage());
				if(clnImg != null) {
					effect.setImage(clnImg);
				}
			}
		}

		@Override
		public Image getImage() {
			return effect.getImage();
		}

		@Override
		public void addObserver(Observer o) {
			effect.watch.addObserver(o);
		}

		@Override
		public void deleteObserver(Observer o) {
			effect.watch.deleteObserver(o);
		}
		
	}
	
	/**
	 * Creates a 2x2 set of images representing the first, 1/4, 3/4, and last images of the scene
	 * @author antho
	 *
	 */
	class SceneThumb implements Icon {
		
		SceneView scene;
		
		public SceneThumb(SceneView scene) {
			this.scene = scene;
		}
		
		@Override
		public int getIconHeight() {
			return 72*2+10;
		}

		@Override
		public int getIconWidth() {
			return (int) (128.0*2) + 10;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			if(scene.listModel.isEmpty())return;
			
			Image[] images = new Image[4];
			ImageIcon[] imageIcons = new ImageIcon[4];
			
			images[0] = scene.listModel.firstElement();
			images[3] = scene.listModel.lastElement();
			images[1] = scene.listModel.get(scene.listModel.getSize()/4);
			images[2] = scene.listModel.get(scene.listModel.getSize()*3/4);
			
			for(int i = 0; i < 4; i++) {
				Image img = images[i];
	    		if(!thumbHash.containsKey(img)) {
	    			float ratio = ((float) img.getWidth(null)) / ((float) img.getHeight(null));
	    			Image imageClone = img.getScaledInstance(128, (int) (128.0f/ratio), Image.SCALE_SMOOTH);
	    			imageIcons[i] = new ImageIcon(imageClone);
	    			thumbHash.put(img, imageIcons[i]);
	    		}else {
	    			imageIcons[i] = thumbHash.get(img);
	    		}
			}
    		
    		g.drawImage(imageIcons[0].getImage(), x+5, y+5, null);
    		g.drawImage(imageIcons[1].getImage(), x+128+5, y+5, null);
    		g.drawImage(imageIcons[2].getImage(), x+5, y+5+imageIcons[0].getIconHeight(), null);
    		g.drawImage(imageIcons[3].getImage(), x+128+5, y+5+imageIcons[0].getIconHeight(), null);
    		
    		g.drawRect(x, y, getIconWidth(), getIconHeight());
    		
		}
		
	}
	
	/**
	 * Creates a 2x2 set of images representing the first, 1/4, 3/4, and last images of the story
	 * @author antho
	 *
	 */
	class StoryThumb implements Icon {
		
		StoryView story;
		
		public StoryThumb(StoryView story) {
			this.story = story;
		}
		
		@Override
		public int getIconHeight() {
			return 72*2+10;
		}

		@Override
		public int getIconWidth() {
			return (int) (128.0*2) + 10;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			if(story.listModel.isEmpty())return;
			
			
			Image[] images = new Image[4];
			ImageIcon[] imageIcons = new ImageIcon[4];
			
			int frameCount = story.getFrameCount();
			if(frameCount > 0) {
			
				images[0] = story.getFrame(0);
				images[3] = story.getFrame(frameCount - 1);
				images[1] = story.getFrame(frameCount/4);
				images[2] = story.getFrame(frameCount*3/4);
				
				for(int i = 0; i < 4; i++) {
					Image img = images[i];
		    		if(!thumbHash.containsKey(img)) {
		    			float ratio = ((float) img.getWidth(null)) / ((float) img.getHeight(null));
		    			Image imageClone = img.getScaledInstance(128, (int) (128.0f/ratio), Image.SCALE_SMOOTH);
		    			imageIcons[i] = new ImageIcon(imageClone);
		    			thumbHash.put(img, imageIcons[i]);
		    		}else {
		    			imageIcons[i] = thumbHash.get(img);
		    		}
				}
			}
    		
			if(imageIcons[0] != null)g.drawImage(imageIcons[0].getImage(), x+5, y+5, null);
			if(imageIcons[0] != null)g.drawImage(imageIcons[1].getImage(), x+128+5, y+5, null);
			if(imageIcons[0] != null)g.drawImage(imageIcons[2].getImage(), x+5, y+5+imageIcons[0].getIconHeight(), null);
			if(imageIcons[0] != null)g.drawImage(imageIcons[3].getImage(), x+128+5, y+5+imageIcons[0].getIconHeight(), null);
    		
    		g.drawRect(x, y, getIconWidth(), getIconHeight());
    		
		}
		
	}
	
	/**
	 * https://stackoverflow.com/questions/41223992/icons-similar-to-desktop-shortcuts-inside-jdesktoppane
	 * @author antho
	 *
	 */
	class SimpleDesktopIconUI extends BasicDesktopIconUI {
		private final Icon icon;

		SimpleDesktopIconUI(Icon icon) {
			this.icon = icon;
		}

		@Override
		protected void installComponents() {
			frame = desktopIcon.getInternalFrame();
			String title = frame.getTitle();

			JLabel label = new JLabel(title, icon, SwingConstants.CENTER);
			label.setVerticalTextPosition(JLabel.BOTTOM);
			label.setHorizontalTextPosition(JLabel.CENTER);

			desktopIcon.setBorder(null);
			desktopIcon.setOpaque(false);
			desktopIcon.setLayout(new GridLayout(1, 1));
			desktopIcon.add(label);
		}

		@Override
		protected void uninstallComponents() {
			desktopIcon.setLayout(null);
			desktopIcon.removeAll();
			frame = null;
		}

		@Override
		public Dimension getMinimumSize(JComponent c) {
			LayoutManager layout = desktopIcon.getLayout();
			Dimension size = layout.minimumLayoutSize(desktopIcon);
			return new Dimension(size.width + 15, size.height + 15);
		}

		@Override
		public Dimension getPreferredSize(JComponent c) {
			return getMinimumSize(c);
		}

		@Override
		public Dimension getMaximumSize(JComponent c) {
			return getMinimumSize(c);
		}
	}
	
	/**
	 * Returns a string with whitespace and special characters removed, suitable for file names
	 * @param s
	 * @return
	 */
	public String sanitizeString(String s) {
		return s.replaceAll("\\W+", "");
	}
	
	public void addCamera(int port) {
		if(cameraViews.containsKey(port)) {
			logger.info("A camera on port " + port + " already exists");
			return;
		}
		CameraView camera = new CameraView(port);
		desktop.add(camera);
		cameraViews.put(port, camera);
	}
	
	/**
	 * Build the desktop pane with toolbar and add cameras
	 * @param cameraPorts 
	 */
	public void init(Vector<Integer> cameraPorts) {
		mainFrame = new JFrame("Pengaloid");
		mainFrame.setSize(900, 600);
		sceneViews = new TreeMap<String, SceneView>();
		storyViews = new TreeMap<String, StoryView>();
		cameraViews = new TreeMap<Integer, CameraView>();
		compositors = new TreeMap<String, Compositor>();
		effects = new TreeMap<String, ImageEffectPanel>();
		
		desktop = new JDesktopPane();
		toolbar = new JToolBar();
		mainFrame.getContentPane().add("Center", desktop);
		mainFrame.getContentPane().add("North", toolbar);
		
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		for(Integer i : cameraPorts) {
			addCamera(i);
		}
		
		JButton newScene = new JButton("New scene...");
		newScene.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				String sceneName = "Scene " + (sceneViews.size()+1);
				addScene(sceneName);
			}
			
		});
		
		JButton newStory = new JButton("New story...");
		newStory.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				String storyName = "Story " + (storyViews.size()+1);
				addStory(storyName);
			}
			
		});
		
		JButton newCamera = new JButton("Add camera...");
		newCamera.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				String portString = JOptionPane.showInputDialog((Component) e.getSource(), "Camera socket:", 50500);
				if(portString != null) {
					try {
						int p = Integer.parseInt(portString);
						addCamera(p);
					} catch(Exception exc) {
						logger.warning(exc.getMessage());
					}
				}
			}
			
		});
		
		JButton setWorkingFolder = new JButton("Set Working Folder");
		
		setWorkingFolder.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser chooser = new JFileChooser();
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				int opt = chooser.showSaveDialog((Component) arg0.getSource());
				if(opt == JFileChooser.APPROVE_OPTION) {
					File f = chooser.getSelectedFile();
					if(!f.exists()) {
						f.mkdir();
					}
					workingFolder = f;
					logger.info("Working folder set to " + workingFolder.getAbsolutePath());
				}
				File props = new File(workingFolder, "project.properties");
				if(props.exists()) {
					restore();
				};
			}
			
		});
		
		JButton openProject = new JButton("Load Project");
		
		openProject.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				JFileChooser chooser = new JFileChooser();
				chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				int opt = chooser.showOpenDialog((Component) arg0.getSource());
				if(opt == JFileChooser.APPROVE_OPTION) {
					File f = chooser.getSelectedFile();
					if(!f.exists()) {
						f.mkdir();
					}
					workingFolder = f;
					logger.info("Working folder set to " + workingFolder.getAbsolutePath());
				}
				File props = new File(workingFolder, "project.properties");
				if(props.exists()) {
					restore();
				};
			}
			
		});
		
		JButton save = new JButton("Save");
		save.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent arg0) {
				if(workingFolder == null) {
					logger.warning("Working folder not set");
					return;
				}
				if(!workingFolder.exists()) {
					if(!workingFolder.mkdir()) {
						logger.warning("Failed to create working folder");
						return;
					}
				}
				store();
			}
			
		});
		
		JButton deleteScene = new JButton("Delete Scene");
		deleteScene.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JList<String> toDelete = new JList<String>(new Vector<String>(sceneViews.keySet()));
				toDelete.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
				JScrollPane sp = new JScrollPane(toDelete);
				JPanel panel = new JPanel(new BorderLayout());
				panel.add("Center", sp);
				panel.add("North", new JLabel("Select scenes to delete"));
				int result = JOptionPane.showConfirmDialog((Component) e.getSource(), panel);
				if(result == JOptionPane.OK_OPTION) {
					List<String> items = toDelete.getSelectedValuesList();
					for(String s : items) {
						deleteScene(s);
					}
				}
			}
			
		});
		
		JButton deleteStory = new JButton("Delete Story");
		deleteStory.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JList<String> toDelete = new JList<String>(new Vector<String>(storyViews.keySet()));
				toDelete.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
				JScrollPane sp = new JScrollPane(toDelete);
				JPanel panel = new JPanel(new BorderLayout());
				panel.add("Center", sp);
				panel.add("North", new JLabel("Select stories to delete"));
				int result = JOptionPane.showConfirmDialog((Component) e.getSource(), panel);
				if(result == JOptionPane.OK_OPTION) {
					List<String> items = toDelete.getSelectedValuesList();
					for(String s : items) {
						deleteStory(s);
					}
				}
			}
			
		});
		
		JButton addCompositor = new JButton("Add Compositor");
		addCompositor.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				String name = "Compositor " + compositors.size();
				addCompositor(name);
			}
			
		});
		
		JButton addEffect = new JButton("Add Effect");
		addEffect.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				String name = "Effect " + effects.size();
				addEffect(name);
			}
			
		});
		
		JButton tile = new JButton("Tile");
		tile.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				tile(desktop);
			}
			
		});
		
		toolbar.add(setWorkingFolder);
		toolbar.add(openProject);
		toolbar.add(save);
		toolbar.add(newCamera);
		toolbar.add(newScene);
		toolbar.add(newStory);
		toolbar.add(deleteScene);
		toolbar.add(deleteStory);
		toolbar.add(addCompositor);
		toolbar.add(addEffect);
		toolbar.add(tile);
		
	}
	
	SceneView addScene(String sceneName) {
		SceneView view = new SceneView(sceneName);
		sceneViews.put(sceneName, view);
		for(CameraView cv : cameraViews.values()) {
			DefaultComboBoxModel<String> scenes = (DefaultComboBoxModel<String>) cv.sceneSelector.getModel();
			scenes.addElement(sceneName);
		}
		for(Compositor cv : compositors.values()) {
			cv.updateSources();
		}
		view.setVisible(true);
		view.setSize(new Dimension(300, 300));
		desktop.add(view);
		view.getDesktopIcon().setUI(new SimpleDesktopIconUI(new SceneThumb(view)));
		view.pack();
		return view;
	}
	
	void deleteScene(String sceneName) {
		SceneView view = sceneViews.get(sceneName);
		//need to remove any references to this scene from any stories
		for(StoryView sv : storyViews.values()) {
			do {
				
			} while(sv.listModel.removeElement(sceneName));
		}
		if(view == null) {
			logger.warning(sceneName + " does not exist");
		}
		for(CameraView cv : cameraViews.values()) {
			DefaultComboBoxModel<String> scenes = (DefaultComboBoxModel<String>) cv.sceneSelector.getModel();
			scenes.removeElement(sceneName);
		}
		desktop.remove(view);
		sceneViews.remove(sceneName);
		view.dispose();
		desktop.repaint();
	}
	
	StoryView addStory(String storyName) {
		StoryView view = new StoryView(storyName);
		storyViews.put(storyName, view);
		for(SceneView sv : sceneViews.values()) {
			DefaultComboBoxModel<String> stories = (DefaultComboBoxModel<String>) sv.storySelector.getModel();
			stories.addElement(storyName);
		}
		view.setVisible(true);
		view.setSize(new Dimension(300, 300));
		desktop.add(view);
		view.getDesktopIcon().setUI(new SimpleDesktopIconUI(new StoryThumb(view)));
		view.pack();
		return view;
	}
	
	void deleteStory(String storyName) {
		StoryView view = storyViews.get(storyName);
		if(view == null) {
			logger.warning(storyName + " does not exist");
		}
		for(SceneView sv : sceneViews.values()) {
			DefaultComboBoxModel<String> stories = (DefaultComboBoxModel<String>) sv.storySelector.getModel();
			stories.removeElement(storyName);
		}
		desktop.remove(view);
		storyViews.remove(storyName);
		view.dispose();
		desktop.repaint();
	}
	
	void addCompositor(String name) {
		Compositor view = compositors.get(name);
		if(view != null) {
			logger.warning(name + " already exists");
			return;
		}
		view = new Compositor(name);
		view.setVisible(true);
		view.setSize(new Dimension(300, 300));
		desktop.add(view);
		//view.getDesktopIcon().setUI(new SimpleDesktopIconUI(new StoryThumb(view)));
		view.pack();
		compositors.put(name, view);
	}
	
	void addEffect(String name) {
		ImageEffectPanel view = effects.get(name);
		if(view != null) {
			logger.warning(name + " already exists");
			return;
		}
		view = new ImageEffectPanel(name);
		view.setVisible(true);
		view.setSize(new Dimension(300, 300));
		desktop.add(view);
		//view.getDesktopIcon().setUI(new SimpleDesktopIconUI(new StoryThumb(view)));
		view.pack();
		effects.put(name, view);
	}
	
	/**
	 * Converts a given Image into a BufferedImage
	 *
	 * @param img The Image to be converted
	 * @return The converted BufferedImage
	 */
	public static BufferedImage toBufferedImage(Image img)
	{
	    if (img instanceof BufferedImage)
	    {
	        return (BufferedImage) img;
	    }

	    // Create a buffered image with transparency
	    BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

	    // Draw the image on to the buffered image
	    Graphics2D bGr = bimage.createGraphics();
	    bGr.drawImage(img, 0, 0, null);
	    bGr.dispose();

	    // Return the buffered image
	    return bimage;
	}
	
	public void storeImage(File f, Image image) {
		try {
			if(!f.exists()) {
				f.createNewFile();
			}
		    ImageIO.write(toBufferedImage(image), "png", f);
		} catch (IOException e) {
		    logger.warning(e.getMessage());
		}
	}
	
	private void clean() throws IOException {
		if(workingFolder != null && workingFolder.exists()) {
			File project = new File(workingFolder, "project.properties");
			InputStream input;
			input = new FileInputStream(project);
			Properties properties = new Properties();
			properties.load(input);
			String sceneList = properties.getProperty("scenes", null);
			if(sceneList != null) {
				String[] sceneNames = sceneList.split(",");
				for(String s : sceneNames) {
					String dirName = sanitizeString(s);
					File sceneDir = new File(workingFolder, dirName);
					if(sceneDir.exists()) {
						File[] files = sceneDir.listFiles();
						for(File f : files) {
							if(f.getName().endsWith(".png")) {
								logger.fine("Deleting " + f.getAbsolutePath());
								f.delete();
							}
						}
						logger.info("Deleting " + sceneDir.getAbsolutePath());
						sceneDir.delete();
					}
				}
			}
			project.delete();
		}
	}
	
	public void store() {

		try {
			
			try {
				clean();
			} catch(IOException e) {
				logger.warning("Failed to clean working folder: " + e.getMessage());
			}
			
			Properties properties = new Properties();

			File project = new File(workingFolder, "project.properties");
			OutputStream output = new FileOutputStream(project);
			String sceneList = String.join(",", sceneViews.keySet());
			String storyList = String.join(",", storyViews.keySet());
			properties.setProperty("scenes", sceneList);
			properties.setProperty("stories", storyList);
			for(Map.Entry<String, SceneView> entry : sceneViews.entrySet()) {
				entry.getValue().store(entry.getKey(), properties);
			}
			for(Map.Entry<String, StoryView> entry : storyViews.entrySet()) {
				entry.getValue().store(entry.getKey(), properties);
			}
			properties.store(output, new Date().toString());
		} catch (IOException e) {
			logger.warning("Unable to save project: " + e.getMessage());
		}

	}
	
	public void restore() {

		try {
			Properties properties = new Properties();
			File project = new File(workingFolder, "project.properties");
			InputStream input;
			input = new FileInputStream(project);
			properties.load(input);
			String sceneList = properties.getProperty("scenes", null);
			String storyList = properties.getProperty("stories", null);
			if(sceneList != null) {
				String[] sceneNames = sceneList.split(",");
				for(String s : sceneNames) {
					SceneView view = addScene(s);
					view.restore(s, properties);
				}
			}
			if(storyList != null) {
				String[] storyNames = storyList.split(",");
				for(String s : storyNames) {
					StoryView view = addStory(s);
					view.restore(s, properties);
				}
			}
			tile(desktop);
		} catch (FileNotFoundException e) {
			logger.warning(e.getMessage());
		} catch (IOException e) {
			logger.warning(e.getMessage());
		}
		
	}
	
	public void showGUI() {
		mainFrame.setVisible(true);
		tile(desktop);
	}
	
	/**
	 * https://coderanch.com/t/549771/java/Aspect-ratio-Components
	 * @author antho
	 *
	 */
	class ProportionalPanel extends JPanel {

		double proportion;

		public ProportionalPanel(int x, int y) {
			this((double) x / y);
		}

		public ProportionalPanel(double proportion) {
			this.proportion = proportion;
		}

		@Override
		public Dimension getPreferredSize() {
			return new ProportionalDimension(super.getPreferredSize(), proportion);
		}
	}

	class ProportionalDimension extends Dimension {

		public ProportionalDimension(Dimension d, double proportion) {
			double x = d.width;
			double y = d.height;
			if (x / y < proportion) {
				width = (int) (y * proportion);
				height = (int) y;
			} else {
				width = (int) x;
				height = (int) (x / proportion);
			}
		}
	}
	
	/**
	 * From <a href=
	 * "https://www.java-tips.org/how-to-tile-all-internal-frames-when-requested.html">java-tips.org</a>
	 * <br>
	 * Modified to account for iconified windows.
	 * 
	 * @param desk
	 *            Desktop pane containing windows to be tiled.
	 */
	public static void tile(JDesktopPane desk) {
		// How many frames do we have?
		JInternalFrame[] frames = desk.getAllFrames();
		List<JInternalFrame> tileableFrames = new ArrayList<>();
		List<JInternalFrame> iconifiedFrames = new ArrayList<>();
		boolean icons = false;
		int count = 0;
		for (JInternalFrame frame : frames) {
			if (frame.isIcon()) {
				iconifiedFrames.add(frame);
				icons = true;
			} else {
				tileableFrames.add(frame);
				count++;
			}
		}
		if (count == 0)
			return;

		// Determine the necessary grid size
		int sqrt = (int) Math.sqrt(count);
		int rows = sqrt;
		int cols = sqrt;
		if (rows * cols < count) {
			cols++;
			if (rows * cols < count) {
				rows++;
			}
		}

		// Define some initial values for size & location.
		Dimension size = desk.getSize();
		int hb = size.height;
		if (icons && hb > 27) {
			hb -= 27;
		}
		int w = size.width / cols;
		int h = hb / rows;

		int x = 0;
		int y = 0;

		// Iterate over the frames and relocating & resizing each.
		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < cols && ((i * cols) + j < count); j++) {
				JInternalFrame f = tileableFrames.get((i * cols) + j);
				desk.getDesktopManager().resizeFrame(f, x, y, w, h);
				f.pack();
				//desk.repaint();
				x += w;
			}
			y += h; // start the next row
			x = 0;
		}
	}
	
	public Image cloneImage(Image image) {
		if(image == null)return null;
		BufferedImage copyOfImage = 
				   new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
				Graphics g = copyOfImage.createGraphics();
				g.drawImage(image, 0, 0, null);
				g.dispose();
		return copyOfImage;
	}
	
	public Image replaceBackground(Image image, BufferedImage scenery, Color bg, int thresh) throws Exception {
		BufferedImage result = (BufferedImage) cloneImage(image);
		
		if(result == null)return null;
		
		int w = result.getWidth();
		int h = result.getHeight();
		
		int sw = scenery.getWidth(null);
		int sh = scenery.getHeight(null);
		
		for(int i = 0; i < w; i++) {
			for(int j = 0; j < h; j++) {
				Color c = new Color(result.getRGB(i, j));
				if(Math.abs(c.getRed()-bg.getRed()) > thresh)continue;
				if(Math.abs(c.getGreen()-bg.getGreen()) > thresh)continue;
				if(Math.abs(c.getBlue()-bg.getBlue()) > thresh)continue;
				//must be background
				if(i < sw && j < sh) {
					result.setRGB(i, j, scenery.getRGB(i, j));
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Launch the application, CameraViews will be created on supplied ports for any integer arguments
	 * @param args
	 */
	public static void main(String[] args) {
		LookAndFeel laf = new NimbusLookAndFeel();
		try {
			UIManager.setLookAndFeel(laf);
		} catch (UnsupportedLookAndFeelException e) {
			System.out.println("Unable to set look and feel: " + e.getMessage());
		}
		WorkSurface app = new WorkSurface();
		
		//make a camera for each supplied port
		Vector<Integer> cameraPorts = new Vector<Integer>();
		for(String s : args) {
			try {
				int p = Integer.parseInt(s);
				cameraPorts.add(p);
			} catch (Exception e) {
				
			}
		}
		
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				app.init(cameraPorts);
				
				app.showGUI();
			}
			
		});
		
	}

}
